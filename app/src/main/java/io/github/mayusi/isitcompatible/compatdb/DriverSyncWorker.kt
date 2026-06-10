package io.github.mayusi.isitcompatible.compatdb

import android.content.Context
import android.util.Log
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
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import java.util.concurrent.TimeUnit

/**
 * v0.6: queries the K11MCH1 GitHub Releases page and stamps every Turnip
 * driver row with the latest upstream tag so the UI can show
 * "newer version available" hints.
 *
 * Runs daily on unmetered networks. Never blocks downloads — driver download
 * URLs in seed JSON stay valid for offline-use. This worker only updates
 * metadata about *what's newer than what we shipped*.
 */
@HiltWorker
class DriverSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetcher: DriverFetcher,
    private val driverDao: DriverDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val latest = fetcher.fetchLatest()
            if (latest.isEmpty()) {
                Log.i(TAG, "No upstream tags returned; leaving rows untouched")
                return Result.success()
            }
            val now = System.currentTimeMillis()
            val drivers = driverDao.all()
            var updates = 0
            for (drv in drivers) {
                val family = classifyId(drv.id) ?: continue
                val upstreamTag = latest[family] ?: continue
                // Only stamp when the upstream tag differs from what the row
                // is "named" — heuristic to avoid spamming "newer" when we're
                // already on the latest.
                val newerThanShipped = upstreamTag != drv.upstreamLatestTag &&
                    !drv.name.contains(versionFromTag(upstreamTag), ignoreCase = true)
                if (newerThanShipped) {
                    driverDao.setUpstreamLatest(drv.id, upstreamTag, now)
                    updates++
                } else {
                    // Even when no newer tag is available, refresh dataAsOf
                    // so the "verified <date>" line stays current.
                    driverDao.setUpstreamLatest(drv.id, drv.upstreamLatestTag, now)
                }
            }
            Log.i(TAG, "Stamped ${drivers.size} drivers, $updates flagged as outdated")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Driver sync failed", t)
            Result.retry()
        }
    }

    /**
     * Maps our internal driver id back to its upstream family. Hand-coded
     * because the id naming convention is ours, not the upstream's.
     *
     *  - "turnip-*", "turnip-mr*" → Mesa Turnip (TURNIP_STABLE).
     *  - "adreno-*" / "qualcomm-*" → Qualcomm proprietary (QUALCOMM_STABLE).
     *  - "adreno-stock" specifically → null (don't track; it's the OEM blob).
     */
    private fun classifyId(id: String): DriverFetcher.DriverFamily? {
        val lower = id.lowercase()
        return when {
            lower == "adreno-stock" -> null
            lower.startsWith("turnip") -> DriverFetcher.DriverFamily.TURNIP_STABLE
            lower.startsWith("qualcomm") || lower.startsWith("adreno") ->
                DriverFetcher.DriverFamily.QUALCOMM_STABLE
            else -> null
        }
    }

    /** Pulls "24.3.0" out of "v24.3.0_R6" or "v26.0.0-rc08" for fuzzy "already current" check. */
    private fun versionFromTag(tag: String): String {
        return tag.removePrefix("v").removePrefix("V")
            .substringBefore("_")
            .substringBefore("-")
    }

    companion object {
        const val TAG = "DriverSync"
        const val WORK_NAME = "driver-sync-periodic"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<DriverSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES) // stagger after CompatDbSync
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
