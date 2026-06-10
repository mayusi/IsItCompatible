package io.github.mayusi.isitcompatible.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.getit.GameNativeLaunch
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Browse tab. The default state is **all games in the DB**, sorted alphabetically.
 *
 * Each row carries a pre-computed "best on your device" summary
 * ([GameSummary.bestFps] + [GameSummary.bestEmulatorName] + [GameSummary.bestStability])
 * so the user can see compatibility at a glance without tapping into every detail
 * screen. Sounds expensive but it's done once at load time and cached.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val emulatorDao: EmulatorDao,
    private val journalDao: JournalDao,
    private val prefs: UserPreferences,
    private val compatDb: CompatDbRepository,
) : ViewModel() {

    private val recommender = Recommender()
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        // Observe prefs for the quick-start card gating (wizard + dismissed flag).
        prefs.data
            .onEach { snap ->
                _state.update {
                    it.copy(
                        wizardComplete = snap.wizardComplete,
                        windowsQuickStartDismissed = snap.windowsQuickStartDismissed,
                        deviceFingerprint = snap.fingerprint,
                        iicInstalled = GameNativeLaunch.isIicInstalled(context),
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            compatDb.ready.first { it }
            val fp = prefs.data.first().fingerprint

            // v0.7 Chunk 7.3: pull every DB read on IO, then move the recommender
            // (CPU-bound, no IO) onto Default. Previously this all ran on the
            // main dispatcher AND issued 1088 separate "WHERE gameId=" queries
            // — cold launch took ~3-5s on Odin. With batching + dispatcher
            // discipline it lands in well under 1.5s.
            val (allGames, emusById, reportsByGame) = withContext(Dispatchers.IO) {
                val games = gameDao.all()
                val emus = emulatorDao.all().associateBy { it.id }
                // SQLite caps IN-clauses at ~999 params; batch defensively.
                val grouped = HashMap<String, List<ReportEntity>>()
                games.map { it.id }.chunked(500).forEach { chunk ->
                    val rows = reportDao.byGameIds(chunk)
                    rows.groupBy { it.gameId }.forEach { (k, v) -> grouped[k] = v }
                }
                Triple(games, emus, grouped)
            }

            val summaries = withContext(Dispatchers.Default) {
                allGames.map { game ->
                    val rawReports = reportsByGame[game.id].orEmpty()
                    // Policy: Windows games are GameNative-only. Filter out any
                    // reports for other runners (winlator/cmod/mobox/gamehub) so
                    // they never out-score GameNative in the recommender — even
                    // if the seed still contains older OUR_GITHUB / EMUREADY_SNAPSHOT
                    // entries referencing those runners.
                    val reports = if (game.platform.equals("WINDOWS", ignoreCase = true)) {
                        rawReports.filter { it.emulatorId.equals("gamenative", ignoreCase = true) }
                    } else rawReports
                    val top = if (fp != null && reports.isNotEmpty())
                        recommender.rank(reports, fp, topK = 1).firstOrNull()
                    else null
                    GameSummary(
                        game = game,
                        bestFps = top?.avgFps,
                        bestStability = top?.stability,
                        bestEmulatorName = top?.emulatorId?.let { emusById[it]?.name } ?: top?.emulatorId,
                        bestConfidence = top?.bucket?.confidence,
                        reportCount = reports.size,
                    )
                }
            }

            val platforms = allGames.map { it.platform }.toSortedSet().toList()
            _state.update {
                it.copy(
                    allSummaries = summaries,
                    platforms = platforms,
                    results = summaries.sortedBy { s -> s.game.title },
                    loaded = true,
                )
            }
        }
        // v0.5: track which games the user has logged at least one journal entry
        // for. The Browse row uses this to render a tiny "tried" marker.
        journalDao.observeTriedGameIds()
            .onEach { ids ->
                _state.update { it.copy(triedGameIds = ids.toSet()) }
            }
            .launchIn(viewModelScope)
    }

    /** Dismiss the Windows quick-start onboarding card — persisted, never shown again. */
    fun dismissWindowsQuickStart() {
        viewModelScope.launch { prefs.dismissWindowsQuickStart() }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        refilter()
    }

    fun togglePlatform(platform: String) {
        _state.update {
            val newFilter = if (it.platformFilter == platform) null else platform
            it.copy(platformFilter = newFilter)
        }
        refilter()
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
        refilter()
    }

    fun toggleStabilityFilter() {
        _state.update {
            it.copy(stabilityFilter = if (it.stabilityFilter == null) "runs_great" else null)
        }
        refilter()
    }

    private fun refilter() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(60)
            val s = _state.value
            val needle = s.query.trim().lowercase()
            val tokens = needle.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val comparator: Comparator<GameSummary> = when (s.sortOrder) {
                SortOrder.ALPHA -> compareBy { it.game.title }
                SortOrder.FPS_DESC -> Comparator<GameSummary> { a, b ->
                    val fa = a.bestFps; val fb = b.bestFps
                    when {
                        fa == null && fb == null -> a.game.title.compareTo(b.game.title)
                        fa == null -> 1
                        fb == null -> -1
                        else -> if (fb != fa) fb.compareTo(fa) else a.game.title.compareTo(b.game.title)
                    }
                }
                SortOrder.REPORTS_DESC -> compareByDescending<GameSummary> { it.reportCount }.thenBy { it.game.title }
                SortOrder.FPS_ASC -> Comparator<GameSummary> { a, b ->
                    val fa = a.bestFps; val fb = b.bestFps
                    when {
                        fa == null && fb == null -> a.game.title.compareTo(b.game.title)
                        fa == null -> 1
                        fb == null -> -1
                        else -> if (fa != fb) fa.compareTo(fb) else a.game.title.compareTo(b.game.title)
                    }
                }
            }
            val filtered = s.allSummaries
                .asSequence()
                .filter { s.platformFilter == null || it.game.platform == s.platformFilter }
                .filter {
                    if (tokens.isEmpty()) true
                    else {
                        val haystack = "${it.game.title} ${it.game.titleSlug}".lowercase()
                        tokens.all { token -> token in haystack }
                    }
                }
                .filter {
                    s.stabilityFilter == null ||
                        it.bestStability?.uppercase() in listOf("PERFECT", "PLAYABLE")
                }
                .sortedWith(comparator)
                .toList()
            _state.update { it.copy(results = filtered) }
        }
    }
}

data class GameSummary(
    val game: GameEntity,
    val bestFps: Int?,
    val bestStability: String?,
    val bestEmulatorName: String?,
    /**
     * v0.4: how confidently the recommender matched the user's device.
     * The Browse row dims the FPS pill when this is WEAK/VERY_WEAK so users
     * can tell at a glance whether a number was based on a real match or a
     * widened fallback.
     */
    val bestConfidence: Confidence? = null,
    val reportCount: Int,
)

enum class SortOrder(val label: String) {
    ALPHA("A–Z"),
    FPS_DESC("Best performance"),
    REPORTS_DESC("Most reports"),
    FPS_ASC("Worst performance"),
}

data class SearchState(
    val loaded: Boolean = false,
    val query: String = "",
    val platformFilter: String? = null,
    val platforms: List<String> = emptyList(),
    val allSummaries: List<GameSummary> = emptyList(),
    val results: List<GameSummary> = emptyList(),
    /** v0.5: set of game ids the user has at least one journal entry for. */
    val triedGameIds: Set<String> = emptySet(),
    // v0.11: Windows quick-start onboarding card gating.
    val wizardComplete: Boolean = false,
    val windowsQuickStartDismissed: Boolean = false,
    val deviceFingerprint: DeviceFingerprint? = null,
    /** True when the IIC fork (app.gamenative.iic) is installed. */
    val iicInstalled: Boolean = false,
    /** Current sort order for the Browse list. */
    val sortOrder: SortOrder = SortOrder.ALPHA,
    /** When non-null, filter to only PERFECT/PLAYABLE stability results. */
    val stabilityFilter: String? = null,
)
