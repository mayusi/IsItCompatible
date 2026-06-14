package io.github.mayusi.isitcompatible

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.mayusi.isitcompatible.compatdb.VerifiedGuideImporter
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the `io.github.mayusi.isitcompatible.AUTOTUNER_RESULT` broadcast
 * fired by the GameNative IIC fork when the user applies an auto-tuner winning
 * config for a game on their device.
 *
 * On receipt: validates + sanitizes the config JSON (same pipeline as the SAF
 * file-import path in GameDetailViewModel), resolves the IIC game id from the
 * steam app id, and persists a [LocalVerifiedGuideEntity] (Tier 1) so the next
 * time the user opens that game's detail screen the VerdictCard shows a real
 * device-specific recommendation instead of "ESTIMATED · NO REAL REPORTS YET".
 *
 * SECURITY:
 *   The receiver is exported=true so the cross-app broadcast from the fork
 *   (different package) can reach us on Android 8+.  A malicious app COULD
 *   send a fake AUTOTUNER_RESULT.  Mitigations applied:
 *    - Action is validated explicitly.
 *    - All extras are read defensively; malformed or out-of-range values are
 *      rejected before any write.
 *    - The config JSON is treated as UNTRUSTED and goes through
 *      [VerifiedGuideImporter.validateAndSanitize] which enforces the same
 *      schema checks as the SAF-import path. A malicious config cannot inject
 *      code — the app only reads a flat JSON that GameNative reads back later.
 *    - appId is validated (must be > 0), and if it doesn't match a known game
 *      in the DB the broadcast is silently ignored.
 *    - Worst case: a malicious sender stores a nonsensical config for a game
 *      the user owns. The user can clear it by re-running the auto-tuner or
 *      re-importing their real config.
 *   For a personal-use app (self-signed, sideloaded) this tradeoff is
 *   acceptable. A production app would use a custom signature-level permission.
 *
 * UX: silent save — no notification. The next time the user opens that game's
 * detail screen the VerdictCard updates automatically because the resolver now
 * finds a Tier-1 guide. No extra UI complexity needed.
 */
@AndroidEntryPoint
class AutoTunerResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutoTunerResultReceiver"
        private const val ACTION = "io.github.mayusi.isitcompatible.AUTOTUNER_RESULT"

        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_GAME_SOURCE = "game_source"
        private const val EXTRA_CONFIG_JSON = "config_json"
        private const val EXTRA_AVG_FPS = "avg_fps"
        private const val EXTRA_STABILITY = "stability"
        private const val EXTRA_GOAL = "goal"
        private const val EXTRA_APPLIED_FIXES = "applied_fixes"

        /** Accepted game sources — only STEAM maps to steamAppId in our schema. */
        private val ACCEPTED_SOURCES = setOf("STEAM", "CUSTOM_GAME")

        /** Valid stability values; default to PLAYABLE for anything else. */
        private val VALID_STABILITY = setOf("PERFECT", "PLAYABLE", "GLITCHY", "CRASHES")
    }

    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var verifiedGuideImporter: VerifiedGuideImporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Validate action.
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected action '${intent.action}' — ignoring")
            return
        }

        // 2. Extract + validate extras defensively.
        val appId: Int = intent.getIntExtra(EXTRA_APP_ID, 0)
        val gameSource: String = intent.getStringExtra(EXTRA_GAME_SOURCE)?.trim()?.uppercase() ?: ""
        val configJson: String = intent.getStringExtra(EXTRA_CONFIG_JSON)?.trim() ?: ""
        val avgFps: Float = intent.getFloatExtra(EXTRA_AVG_FPS, 0f)
        val stability: String = run {
            val raw = intent.getStringExtra(EXTRA_STABILITY)?.trim()?.uppercase() ?: ""
            if (raw in VALID_STABILITY) raw else "PLAYABLE"
        }
        val goal: String = intent.getStringExtra(EXTRA_GOAL)?.trim() ?: ""
        val appliedFixes: String = intent.getStringExtra(EXTRA_APPLIED_FIXES)?.trim() ?: "[]"

        Log.i(
            TAG,
            "Received auto-tuner result: appId=$appId src=$gameSource " +
                "avgFps=$avgFps stability=$stability goal=$goal",
        )

        if (appId <= 0) {
            Log.d(TAG, "appId <= 0 — cannot resolve game; ignoring broadcast")
            return
        }
        if (configJson.isBlank()) {
            Log.d(TAG, "config_json is blank — nothing to import; ignoring broadcast")
            return
        }
        if (gameSource !in ACCEPTED_SOURCES) {
            Log.d(TAG, "gameSource='$gameSource' not in accepted set — ignoring broadcast")
            return
        }

        // 3. goAsync so the DB work happens off the main thread without killing
        //    the receiver's 10-second window.
        val result = goAsync()
        scope.launch {
            try {
                handleAutoTunerResult(appId, gameSource, configJson, avgFps, stability, goal, appliedFixes)
            } catch (e: Exception) {
                Log.w(TAG, "Error handling auto-tuner result broadcast", e)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handleAutoTunerResult(
        appId: Int,
        gameSource: String,
        configJson: String,
        avgFps: Float,
        stability: String,
        goal: String,
        appliedFixes: String,
    ) {
        // Resolve the IIC game id from the broadcast's numeric app id.
        val gameId: String? = if (gameSource == "STEAM") {
            gameDao.bySteamAppId(appId)?.id
        } else {
            // CUSTOM_GAME: no steamAppId mapping exists in our schema — skip.
            null
        }

        if (gameId == null) {
            Log.d(TAG, "Could not resolve game id for appId=$appId src=$gameSource — ignoring")
            return
        }

        // Build the source label using the stored device fingerprint (if available).
        // This is what shows in the guide tier badge: "Verified by auto-tune on your device · <GPU>"
        val fp = prefs.data.first().fingerprint
        val deviceLine = fp?.let { "${it.socFamily} · ${it.gpuModel}" }
        val avgFpsLabel = if (avgFps > 0f) " · ~${avgFps.toInt()} fps" else ""
        val goalLabel = if (goal.isNotBlank()) " · goal: $goal" else ""
        val sourceLabel = buildString {
            append("Verified by auto-tune on your device")
            if (deviceLine != null) append(" · $deviceLine")
            append(avgFpsLabel)
            append(goalLabel)
        }

        // Journal notes: honest description of what the auto-tuner measured.
        val journalNotes = buildString {
            append("Auto-tuner verified this config on your device")
            if (avgFps > 0f) append(" (~${avgFps.toInt()} fps avg)")
            if (goal.isNotBlank()) append(", goal: $goal")
            append(".")
            // If the tuner applied compatibility fixes, mention them briefly.
            if (appliedFixes != "[]" && appliedFixes.isNotBlank()) {
                append(" Applied fixes: $appliedFixes")
            }
        }

        // Persist as Tier-1 verified guide via the shared importer.
        // The importer treats configJson as UNTRUSTED and runs validate+sanitize.
        val importResult = verifiedGuideImporter.importVerifiedConfig(
            gameId = gameId,
            rawConfigJson = configJson,
            sourceLabel = sourceLabel,
            journalStability = stability,
            journalNotes = journalNotes,
        )

        when (importResult) {
            is VerifiedGuideImporter.ImportResult.Success -> {
                Log.i(
                    TAG,
                    "Auto-tuner result saved as Tier-1 verified guide for gameId=$gameId " +
                        "(appId=$appId avgFps=$avgFps stability=$stability)",
                )
            }
            is VerifiedGuideImporter.ImportResult.Failure -> {
                Log.w(TAG, "Failed to save auto-tuner result for gameId=$gameId: ${importResult.reason}")
            }
        }
    }
}
