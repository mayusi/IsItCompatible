package io.github.mayusi.isitcompatible.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.DriverFetcher
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.SourceCount
import io.github.mayusi.isitcompatible.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v0.6: rewritten for honesty. Surfaces:
 *  - real "Last synced N hours ago" timestamp from DataStore
 *  - per-source report breakdown (bundled / our GitHub / heuristic / etc.)
 *  - journal entry count
 *  - whether the most recent sync actually reached a remote, so we can show
 *    a "bundled only — no remote yet" caveat when our GitHub repo 404s.
 */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repo: CompatDbRepository,
    private val journalDao: JournalDao,
    private val driverDao: DriverDao,
    private val driverFetcher: DriverFetcher,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    init {
        combine(
            repo.gameCountFlow(),
            repo.reportCountFlow(),
            repo.reportSourceBreakdownFlow(),
            journalDao.countFlow(),
            userPrefs.data,
        ) { games, reports, breakdown, journalCount, prefs ->
            _state.update {
                it.copy(
                    games = games,
                    reports = reports,
                    breakdown = breakdown,
                    journalEntries = journalCount,
                    lastSyncEpochMs = prefs.lastSyncEpochMs,
                    lastSyncRemoteReached = prefs.lastSyncRemoteReached,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun refreshNow() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, lastResult = null) }
            try {
                val result = repo.sync(remoteOnly = false)
                // v0.6: also probe K11MCH1 releases inline. The periodic worker
                // covers the daily case, but tapping "Refresh now" should give
                // the user an instant answer.
                val driverInfo = runCatching { syncDrivers() }.getOrDefault(0 to 0)
                val sourceLine = if (result.remoteReached) {
                    "Synced from ${result.sourceTags.joinToString()}"
                } else {
                    "Bundled seed loaded — remote DB unreachable, working offline"
                }
                val driverLine = when {
                    driverInfo.first == 0 -> "Driver check skipped (offline?)"
                    driverInfo.second > 0 -> "Driver check: ${driverInfo.second} newer upstream"
                    else -> "Driver check: all current"
                }
                _state.update {
                    it.copy(
                        syncing = false,
                        lastResult = "$sourceLine · ${result.gameCount} games · ${result.reportCount} reports · $driverLine",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(syncing = false, lastResult = "Sync failed: ${t.message ?: "unknown error"}")
                }
            }
        }
    }

    /**
     * Runs the same flow as [io.github.mayusi.isitcompatible.compatdb.DriverSyncWorker]
     * but inline so a manual "Refresh now" gives instant feedback.
     *
     * @return (tagsFound, newerStamped). tagsFound=0 means we never got a usable
     *         response from upstream — likely no network or rate-limited.
     */
    private suspend fun syncDrivers(): Pair<Int, Int> {
        val latest = driverFetcher.fetchLatest()
        if (latest.isEmpty()) return 0 to 0
        val now = System.currentTimeMillis()
        val drivers = driverDao.all()
        var stampedNewer = 0
        for (drv in drivers) {
            val lower = drv.id.lowercase()
            val family = when {
                lower == "adreno-stock" -> null
                lower.startsWith("turnip") -> DriverFetcher.DriverFamily.TURNIP_STABLE
                lower.startsWith("qualcomm") || lower.startsWith("adreno") ->
                    DriverFetcher.DriverFamily.QUALCOMM_STABLE
                else -> null
            } ?: continue
            val upstream = latest[family] ?: continue
            val versionCore = upstream.removePrefix("v").substringBefore("_").substringBefore("-")
            val alreadyOnIt = drv.name.contains(versionCore, ignoreCase = true)
            if (!alreadyOnIt && upstream != drv.upstreamLatestTag) {
                driverDao.setUpstreamLatest(drv.id, upstream, now)
                stampedNewer++
            } else {
                driverDao.setUpstreamLatest(drv.id, drv.upstreamLatestTag, now)
            }
        }
        return latest.size to stampedNewer
    }
}

data class UpdatesState(
    val games: Int = 0,
    val reports: Int = 0,
    val breakdown: List<SourceCount> = emptyList(),
    val journalEntries: Int = 0,
    val lastSyncEpochMs: Long = 0L,
    val lastSyncRemoteReached: Boolean = false,
    val syncing: Boolean = false,
    val lastResult: String? = null,
)
