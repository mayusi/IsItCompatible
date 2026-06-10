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
import java.util.concurrent.TimeUnit

/**
 * Background DB sync. Runs once a day on unmetered networks, or on demand
 * from the Updates tab.
 */
@HiltWorker
class CompatDbSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CompatDbRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = repo.sync(remoteOnly = false)
            Log.i(TAG, "Sync ok — games=${result.gameCount} reports=${result.reportCount} from ${result.sourceTags}")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Sync failed", t)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CompatDbSync"
        const val WORK_NAME = "compatdb-sync-periodic"

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
