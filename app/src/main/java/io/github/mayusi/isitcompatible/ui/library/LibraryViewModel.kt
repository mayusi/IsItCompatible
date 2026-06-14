package io.github.mayusi.isitcompatible.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.GAMENATIVE_EMULATOR_ID
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.library.LibraryScanner
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.library.ScannedGame
import io.github.mayusi.isitcompatible.recommend.Bucket
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.recommend.Recommendation
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val scanner: LibraryScanner,
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val emulatorDao: EmulatorDao,
    private val compatDb: CompatDbRepository,
    private val recommender: Recommender,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // Wait for first DB seed so the scanner's name-match has data.
                compatDb.ready.first { it }
                val snapshot = prefs.data.first()
                val fp = snapshot.fingerprint
                val rom = snapshot.romFolderUri?.let { Uri.parse(it) }
                val pc = snapshot.pcFolderUri?.let { Uri.parse(it) }

                val games = buildList {
                    rom?.let { addAll(scanner.scanRoms(it)) }
                    pc?.let { addAll(scanner.scanPcGames(it)) }
                }

                // Batch-fetch reports and emulators on IO dispatcher.
                val (reportsByGame, emusById) = withContext(Dispatchers.IO) {
                    val gameIds = games.mapNotNull { it.gameId }
                    val byGame = HashMap<String, List<ReportEntity>>()
                    gameIds.chunked(500).forEach { chunk ->
                        val rows = reportDao.byGameIds(chunk)
                        rows.groupBy { it.gameId }.forEach { (k, v) -> byGame[k] = v }
                    }
                    val emus = emulatorDao.all().associateBy { it.id }
                    byGame to emus
                }

                // Compute compatibility summaries on the Default dispatcher (CPU-bound).
                val items = withContext(Dispatchers.Default) {
                    games.map { sg ->
                        val rawReports = reportsByGame[sg.gameId].orEmpty()
                        summaryFor(sg, fp, rawReports, emusById)
                    }
                }

                val sorted = sortItems(items, _state.value.sortOrder)
                _state.update {
                    it.copy(
                        loading = false,
                        allItems = items,
                        items = sorted,
                        fingerprint = fp,
                        romPicked = rom != null,
                        pcPicked = pc != null,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: "Scan failed") }
            }
        }
    }

    fun setSortOrder(order: LibrarySortOrder) {
        _state.update { s ->
            s.copy(sortOrder = order, items = sortItems(s.allItems, order))
        }
    }

    private fun sortItems(
        items: List<LibraryGameItem>,
        order: LibrarySortOrder,
    ): List<LibraryGameItem> = when (order) {
        LibrarySortOrder.ALPHA -> items.sortedBy { it.game.displayName }
        LibrarySortOrder.FPS_DESC -> items.sortedWith(
            Comparator { a, b ->
                val fa = a.bestFps; val fb = b.bestFps
                when {
                    fa == null && fb == null -> a.game.displayName.compareTo(b.game.displayName)
                    fa == null -> 1
                    fb == null -> -1
                    else -> if (fb != fa) fb.compareTo(fa) else a.game.displayName.compareTo(b.game.displayName)
                }
            },
        )
    }

    /**
     * Compute the full compatibility summary for a scanned game from pre-fetched
     * reports. No DB access — all data has already been batched by [rescan].
     * The dot color is derived from the same recommender pass that produces FPS/
     * stability/emulator data, so it stays consistent.
     */
    private fun summaryFor(
        g: ScannedGame,
        fp: DeviceFingerprint?,
        rawReports: List<ReportEntity>,
        emusById: Map<String, io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity>,
    ): LibraryGameItem {
        if (g.gameId == null || fp == null || rawReports.isEmpty()) {
            return LibraryGameItem(game = g, dot = DotColor.GRAY)
        }
        // Policy: Windows games are GameNative-only.
        // LibraryScanner uses platformGuess (from scanned exe/folder) rather than a GameEntity,
        // so we replicate the same Windows check using the platform guess + gameId prefix.
        val isWindows = g.platformGuess.equals("Windows", ignoreCase = true) ||
            g.gameId.startsWith("win:", ignoreCase = true)
        val reports = if (isWindows) {
            rawReports.filter { it.emulatorId.equals(GAMENATIVE_EMULATOR_ID, ignoreCase = true) }
        } else rawReports
        if (reports.isEmpty()) return LibraryGameItem(game = g, dot = DotColor.GRAY)

        val top: Recommendation = recommender.rank(reports, fp, topK = 1).firstOrNull()
            ?: return LibraryGameItem(game = g, dot = DotColor.GRAY)

        val dot = when (top.bucket) {
            Bucket.SAME_SOC_AND_RAM,
            Bucket.SAME_SOC_FAMILY -> when (top.stability.uppercase()) {
                "PERFECT", "PLAYABLE" -> DotColor.GREEN
                "GLITCHY" -> DotColor.YELLOW
                "CRASHES" -> DotColor.RED
                else -> DotColor.YELLOW
            }
            Bucket.SAME_GPU_VENDOR -> DotColor.YELLOW
            Bucket.ANY_DEVICE -> DotColor.GRAY
        }

        return LibraryGameItem(
            game = g,
            dot = dot,
            bestFps = top.avgFps,
            bestStability = top.stability,
            bestEmulatorName = emusById[top.emulatorId]?.name ?: top.emulatorId,
            bestConfidence = top.bucket.confidence,
            reportCount = reports.size,
        )
    }
}

/** A scanned game enriched with compatibility data for the Library UI. */
data class LibraryGameItem(
    val game: ScannedGame,
    val dot: DotColor,
    val bestFps: Int? = null,
    val bestStability: String? = null,
    val bestEmulatorName: String? = null,
    val bestConfidence: Confidence? = null,
    val reportCount: Int = 0,
)

enum class LibrarySortOrder(val label: String) {
    ALPHA("A–Z"),
    FPS_DESC("Best performance"),
}

data class LibraryState(
    val loading: Boolean = true,
    /** Unsorted master list — re-sorted on demand without a rescan. */
    val allItems: List<LibraryGameItem> = emptyList(),
    val items: List<LibraryGameItem> = emptyList(),
    val fingerprint: DeviceFingerprint? = null,
    val romPicked: Boolean = false,
    val pcPicked: Boolean = false,
    val error: String? = null,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.ALPHA,
)

enum class DotColor { GREEN, YELLOW, RED, GRAY }
