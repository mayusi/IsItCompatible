package io.github.mayusi.isitcompatible.compatdb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mayusi.isitcompatible.compatdb.room.FavoriteDao
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background DB sync. Runs once a day on unmetered networks, or on demand
 * from the Updates tab.
 *
 * Feature B extension: after each sync, diffs the best compatibility state
 * for every favorited game. When a real improvement is detected (e.g. a new
 * PERFECT report appeared, stability improved, or estimated data got real data),
 * posts a local notification. Anti-spam: one notification per game per genuine
 * improvement — the "last known best state" is stored in the favorites table.
 */
@HiltWorker
class CompatDbSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CompatDbRepository,
    private val favoriteDao: FavoriteDao,
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    private val recommender = Recommender()

    override suspend fun doWork(): Result {
        return try {
            val result = repo.sync(remoteOnly = false)
            Log.i(TAG, "Sync ok — games=${result.gameCount} reports=${result.reportCount} from ${result.sourceTags}")
            // Feature B: check favorited games for compatibility improvements.
            checkFavoriteAlerts()
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Sync failed", t)
            Result.retry()
        }
    }

    /**
     * Feature B: for each favorited game, compute the current best state and
     * compare against the stored snapshot. Notify only when a REAL improvement
     * happened (stability improved OR FPS improved by ≥5fps with real data).
     * Updates the stored snapshot so we don't spam the same notification.
     */
    private suspend fun checkFavoriteAlerts() {
        val favorites = favoriteDao.all()
        if (favorites.isEmpty()) return

        val fp: DeviceFingerprint = prefs.data.first().fingerprint ?: return
        ensureNotificationChannel()

        favorites.forEach { fav ->
            try {
                val game = gameDao.byId(fav.gameId) ?: return@forEach
                val reports = reportDao.byGame(fav.gameId)
                    .filter { r ->
                        // Policy: Windows games are GameNative-only.
                        if (game.platform.equals("WINDOWS", ignoreCase = true))
                            r.emulatorId.equals("gamenative", ignoreCase = true)
                        else true
                    }
                    // Only real reports for alerts — no GENERATED_HEURISTIC noise.
                    .filter { !it.source.equals("GENERATED_HEURISTIC", ignoreCase = true) }

                if (reports.isEmpty()) return@forEach

                val bySource = recommender.rankBySource(reports, fp, topK = 1)
                val top = bySource.fromReal.firstOrNull() ?: return@forEach

                // Encode a compact "before vs after" string.
                val newState = "${top.stability}|${top.avgFps ?: 0}|${top.bucket.confidence.name}"
                val oldState = fav.lastKnownBestState

                // Diff: was this a real improvement?
                val improved = isRealImprovement(oldState, newState)
                if (improved) {
                    val fpsStr = top.avgFps?.let { " at ${it}fps" } ?: ""
                    val stabLabel = when (top.stability.uppercase()) {
                        "PERFECT" -> "Perfect"
                        "PLAYABLE" -> "Playable"
                        "GLITCHY" -> "Glitchy"
                        else -> top.stability
                    }
                    postNotification(
                        gameId = fav.gameId,
                        gameTitle = game.title,
                        stability = stabLabel,
                        fpsStr = fpsStr,
                    )
                }
                // Always update the stored state after a sync, even when no improvement.
                // This prevents stale data from triggering false-positive notifications
                // after the DB is reseeded.
                favoriteDao.updateLastKnownBestState(fav.gameId, newState)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed checking favorite ${fav.gameId}", t)
            }
        }
    }

    /**
     * Returns true only when the compatibility GENUINELY improved:
     *  - stability moved up the ladder (CRASHES→GLITCHY→PLAYABLE→PERFECT)
     *  - OR FPS improved by ≥ 5fps with at least the same stability
     *  - OR data went from no-real-data to a real HIGH/STRONG-confidence result
     *
     * Empty oldState ("") counts as the worst possible — any real result is an improvement.
     */
    private fun isRealImprovement(oldState: String, newState: String): Boolean {
        if (oldState == newState) return false
        if (oldState.isBlank()) return true // First real data is always an improvement.

        val (oldStab, oldFps, oldConf) = parseState(oldState)
        val (newStab, newFps, newConf) = parseState(newState)

        val stabOrder = listOf("CRASHES", "GLITCHY", "PLAYABLE", "PERFECT")
        val oldStabIdx = stabOrder.indexOf(oldStab.uppercase())
        val newStabIdx = stabOrder.indexOf(newStab.uppercase())

        // Stability improved
        if (newStabIdx > oldStabIdx) return true
        // FPS improved ≥5 with same or better stability
        if (newStabIdx >= oldStabIdx && newFps - oldFps >= 5) return true
        // Confidence improved from WEAK/VERY_WEAK to STRONG/MODERATE with same stability
        val confOrder = listOf("VERY_WEAK", "WEAK", "MODERATE", "STRONG")
        val oldConfIdx = confOrder.indexOf(oldConf.uppercase())
        val newConfIdx = confOrder.indexOf(newConf.uppercase())
        if (newConfIdx > oldConfIdx + 1 && newStabIdx >= oldStabIdx) return true

        return false
    }

    private data class ParsedState(val stability: String, val fps: Int, val confidence: String)

    private fun parseState(state: String): ParsedState {
        val parts = state.split('|')
        return ParsedState(
            stability = parts.getOrNull(0) ?: "CRASHES",
            fps = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            confidence = parts.getOrNull(2) ?: "VERY_WEAK",
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Compatibility updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when a favorited game gets better compatibility data on your device"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postNotification(gameId: String, gameTitle: String, stability: String, fpsStr: String) {
        // Android 13+ requires POST_NOTIFICATIONS to be granted at runtime.
        // The manifest already declares it; we check here so we never crash or
        // post silently on devices where the user denied it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.d(TAG, "POST_NOTIFICATIONS not granted — skipping alert for $gameTitle")
                return
            }
        }

        // Use gameId hash as notification id so each game gets its own slot
        // and a second improvement replaces the first rather than stacking.
        val notifId = gameId.hashCode()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Compatibility update: $gameTitle")
            .setContentText("Now $stability$fpsStr on your device")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$gameTitle now runs $stability$fpsStr on your device type. " +
                            "Open the app to see the full report.",
                    ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notifId, notification)
            Log.i(TAG, "Notified: $gameTitle improved to $stability$fpsStr")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification — POST_NOTIFICATIONS denied?", e)
        }
    }

    companion object {
        const val TAG = "CompatDbSync"
        const val WORK_NAME = "compatdb-sync-periodic"
        private const val CHANNEL_ID = "compat_updates"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<CompatDbSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES) // breathe before first auto-sync
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
