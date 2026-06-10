package io.github.mayusi.isitcompatible

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.CompatDbSyncWorker
import io.github.mayusi.isitcompatible.compatdb.CoverArtSyncWorker
import io.github.mayusi.isitcompatible.compatdb.DriverSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class IsItCompatibleApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var compatDbRepository: CompatDbRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Seed the DB on first launch / on every cold start with the bundled
        // snapshot so the user sees data immediately. Remote sources fold in
        // on top of this when the WorkManager periodic job runs.
        appScope.launch {
            runCatching { compatDbRepository.sync(remoteOnly = false) }
                .onFailure { Log.w("App", "Initial seed failed", it) }
        }
        CompatDbSyncWorker.schedulePeriodic(this)
        // v0.6: daily check against K11MCH1 GitHub releases so the recommended
        // Turnip stamp stays current. Same unmetered-network constraint.
        DriverSyncWorker.schedulePeriodic(this)
        // v0.7: IGDB cover art backfill — no-ops when credentials aren't set
        // in local.properties, so this is safe to always schedule.
        CoverArtSyncWorker.schedulePeriodic(this)
    }
}
