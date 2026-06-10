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
import io.github.mayusi.isitcompatible.BuildConfig
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import java.util.concurrent.TimeUnit

/**
 * v0.7 Chunk 7.4: walks every GameEntity with a null coverUrl, asks IGDB for
 * a cover image URL, writes it back to Room.
 *
 * Behaviour by environment:
 *  - Without IGDB credentials → does nothing, logs once that it's skipped.
 *  - With credentials → processes up to BATCH_CAP rows per run so a fresh
 *    install doesn't hammer IGDB at install time. With ~1000 rows missing
 *    covers + 4 req/s rate limit, full backfill takes ~5 min per day for ~3 days.
 *  - On HTTP/network failure → graceful no-op, retries next run.
 */
@HiltWorker
class CoverArtSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetcher: IgdbCoverFetcher,
    private val gameDao: GameDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.IGDB_CLIENT_ID.isBlank() || BuildConfig.IGDB_CLIENT_SECRET.isBlank()) {
            Log.i(TAG, "IGDB credentials not configured — skipping cover sync. " +
                "Add IGDB_CLIENT_ID and IGDB_CLIENT_SECRET to local.properties to enable.")
            return Result.success()
        }
        return try {
            val missing = gameDao.gamesMissingCover()
                .take(BATCH_CAP)
                .associate { it.id to it.title }
            if (missing.isEmpty()) {
                Log.i(TAG, "Every game already has a cover — nothing to fetch.")
                return Result.success()
            }
            Log.i(TAG, "Fetching covers for ${missing.size} games (cap=$BATCH_CAP)...")
            val resolved = fetcher.resolveCoverUrls(missing)
            for ((id, url) in resolved) {
                gameDao.setCoverUrl(id, url)
            }
            Log.i(TAG, "Cover sync done: ${resolved.size}/${missing.size} matched.")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Cover sync failed", t)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CoverArtSync"
        const val WORK_NAME = "cover-art-sync-periodic"
        // Cap per run so the worker stays well under WorkManager's 10-min execution budget.
        // 300 lookups at ~260ms each = ~78s; leaves headroom for slow networks.
        const val BATCH_CAP = 300

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<CoverArtSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(45, TimeUnit.MINUTES) // after the DB + driver syncs
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
