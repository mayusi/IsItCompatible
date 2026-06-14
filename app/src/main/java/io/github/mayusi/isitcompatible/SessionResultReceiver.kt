package io.github.mayusi.isitcompatible

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the `io.github.mayusi.isitcompatible.GAME_SESSION_ENDED` broadcast
 * fired by the GameNative IIC fork at game-session end.
 *
 * APPROACH — pending-session-on-open (not notification):
 *   We store a tiny "pending session" in DataStore (game id + session minutes +
 *   showedFps). When the user opens the matching game's detail screen, the
 *   GameDetailViewModel sees it, opens the journal form pre-filled with
 *   emulatorId="gamenative" and sessionMinutes from the broadcast, and clears
 *   the pending-session so it only surfaces once.
 *
 *   Why this approach over a notification deep-link:
 *   - No POST_NOTIFICATIONS permission dance; the app already declares it but
 *     the user may not have granted it.
 *   - Navigation is simpler: the detail screen is already the natural place
 *     for journal entry, and the ViewModel already drives journal form opening.
 *   - Less surface area for races (no pending intent expiry, no back-stack noise).
 *
 * SECURITY TRADEOFF:
 *   The receiver is exported=true so the implicit cross-app broadcast from the
 *   fork (different package, no shared UID) can reach us on Android 8+. An
 *   unexported receiver would silently drop it.
 *
 *   Mitigation applied:
 *   - Action is validated explicitly (guards against accidental intent re-use).
 *   - All extras are read defensively; malformed values (negative minutes, blank
 *     game source) are clamped / rejected before any storage write.
 *   - The worst a malicious sender can do is write a pending-session for a game
 *     that may not exist in our DB — the ViewModel's gameDao.byId() check then
 *     silently no-ops and clears the stale pref, leaking nothing.
 *   - For a personal-use app (self-signed, sideloaded), this tradeoff is
 *     acceptable. A production app would declare a custom signature-level
 *     permission shared between fork and app.
 */
@AndroidEntryPoint
class SessionResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SessionResultReceiver"
        private const val ACTION = "io.github.mayusi.isitcompatible.GAME_SESSION_ENDED"
        private const val SENDER_PACKAGE = "app.gamenative.iic"

        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_GAME_SOURCE = "game_source"
        private const val EXTRA_SESSION_MINUTES = "session_minutes"
        private const val EXTRA_SHOWED_FPS = "showed_fps"
        // Feature C: forward-compat extras — sent by a future fork update.
        // Current fork (v1.x) does not send these; the receiver degrades gracefully.
        private const val EXTRA_AVG_FPS = "avg_fps"
        private const val EXTRA_STABILITY = "stability"

        /** Valid stability values; anything else is rejected. */
        private val VALID_STABILITIES = setOf("PERFECT", "PLAYABLE", "GLITCHY", "CRASHES")
    }

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var gameDao: GameDao

    // BroadcastReceiver lifecycle is very short — use goAsync() scope with
    // SupervisorJob so we can do the DataStore write off the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Validate action.
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected action '${intent.action}' — ignoring")
            return
        }

        // 2. Extract + validate extras.
        val appId: Int = intent.getIntExtra(EXTRA_APP_ID, 0)
        val gameSource: String = intent.getStringExtra(EXTRA_GAME_SOURCE)?.trim()?.uppercase()
            ?: ""
        val sessionMinutes: Int = intent.getIntExtra(EXTRA_SESSION_MINUTES, 0).coerceAtLeast(0)
        val showedFps: Boolean = intent.getBooleanExtra(EXTRA_SHOWED_FPS, false)
        // Feature C: forward-compat extras. The current fork does not send these;
        // getIntExtra / getStringExtra return -1 / null when absent.
        val avgFpsRaw: Int = intent.getIntExtra(EXTRA_AVG_FPS, -1)
        val avgFps: Int? = if (avgFpsRaw in 0..999) avgFpsRaw else null
        val stabilityRaw: String? = intent.getStringExtra(EXTRA_STABILITY)?.trim()?.uppercase()
        val stability: String? = if (stabilityRaw != null && stabilityRaw in VALID_STABILITIES)
            stabilityRaw else null

        Log.i(TAG, "Received session-ended: appId=$appId src=$gameSource mins=$sessionMinutes fps=$showedFps avgFps=$avgFps stability=$stability")

        if (appId <= 0) {
            Log.d(TAG, "appId <= 0 — cannot resolve game; ignoring broadcast")
            return
        }

        // 3. Use goAsync so the DataStore + DB lookups happen off the main thread
        //    without killing the receiver's timeout.
        val result = goAsync()
        scope.launch {
            try {
                handleSessionEnded(appId, gameSource, sessionMinutes, showedFps, avgFps, stability)
            } catch (e: Exception) {
                Log.w(TAG, "Error handling session-ended broadcast", e)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handleSessionEnded(
        appId: Int,
        gameSource: String,
        sessionMinutes: Int,
        showedFps: Boolean,
        avgFps: Int? = null,
        stability: String? = null,
    ) {
        // Resolve the IIC game id from the broadcast's numeric app id + source.
        // GameNative only handles STEAM games (all its store ids are Steam app ids).
        // For other sources we try a best-effort lookup but Steam is primary.
        val gameId: String? = resolveGameId(appId, gameSource)

        if (gameId == null) {
            Log.d(TAG, "Could not resolve game id for appId=$appId src=$gameSource — ignoring")
            return
        }

        // 4. Persist the pending session. GameDetailViewModel will pick it up
        //    the next time the user opens that game's detail screen.
        prefs.setPendingSession(
            gameId = gameId,
            sessionMinutes = sessionMinutes.takeIf { it > 0 },
            showedFps = showedFps,
            avgFps = avgFps,         // Feature C: null if fork didn't send it
            stability = stability,   // Feature C: null if fork didn't send it
        )
        Log.i(TAG, "Pending session stored for gameId=$gameId (mins=$sessionMinutes fps=$showedFps avgFps=$avgFps stability=$stability)")
    }

    /**
     * Resolve the IIC-internal [GameEntity.id] from the broadcast's numeric id
     * and game source. The IIC DB uses stable composite ids built from
     * `platform|titleSlug`; the only cross-app link we have is steamAppId stored
     * on [GameEntity]. We query the game table by steamAppId for STEAM games.
     *
     * For non-STEAM sources there is no steamAppId column equivalent yet, so we
     * return null and the broadcast is silently ignored — correct behaviour since
     * GameNative's main use-case is Steam/Windows titles.
     */
    private suspend fun resolveGameId(appId: Int, gameSource: String): String? {
        // Only Steam app IDs map to GameEntity.steamAppId in our schema.
        if (gameSource != "STEAM" && gameSource != "CUSTOM_GAME") {
            // GOG/Epic/Amazon game ids in GameNative don't map to steamAppId; skip.
            return null
        }
        return gameDao.bySteamAppId(appId)?.id
    }
}
