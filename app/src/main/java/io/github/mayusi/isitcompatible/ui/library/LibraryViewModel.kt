package io.github.mayusi.isitcompatible.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.library.LibraryScanner
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.library.ScannedGame
import io.github.mayusi.isitcompatible.recommend.Bucket
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val scanner: LibraryScanner,
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val compatDb: CompatDbRepository,
) : ViewModel() {

    private val recommender = Recommender()

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

                // Batch-fetch all reports for matched games in one pass (O(1) DB
                // round-trips, chunked ≤500 to respect SQLite's IN-clause limit).
                // Previously this was O(N) — one reportDao.byGame() per scanned game.
                val gameIds = games.mapNotNull { it.gameId }
                val reportsByGame = HashMap<String, List<ReportEntity>>()
                gameIds.chunked(500).forEach { chunk ->
                    val rows = reportDao.byGameIds(chunk)
                    rows.groupBy { it.gameId }.forEach { (k, v) -> reportsByGame[k] = v }
                }

                val withDots = games.map { sg ->
                    sg to dotFor(sg, fp, reportsByGame[sg.gameId].orEmpty())
                }
                _state.update {
                    it.copy(
                        loading = false,
                        games = withDots,
                        fingerprint = fp,
                        romPicked = rom != null,
                        pcPicked = pc != null,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.message ?: "Scan failed") }
            }
        }
    }

    /**
     * Compute the library-dot color for a scanned game from pre-fetched reports.
     * No DB access — all reports have already been batched by [rescan].
     * Color logic is identical to the previous per-game version.
     */
    private fun dotFor(
        g: ScannedGame,
        fp: DeviceFingerprint?,
        rawReports: List<ReportEntity>,
    ): DotColor {
        if (g.gameId == null || fp == null) return DotColor.GRAY
        if (rawReports.isEmpty()) return DotColor.GRAY
        // Policy: Windows games are GameNative-only.
        val isWindows = g.platformGuess.equals("Windows", ignoreCase = true) ||
            g.gameId.startsWith("win:", ignoreCase = true)
        val reports = if (isWindows) {
            rawReports.filter { it.emulatorId.equals("gamenative", ignoreCase = true) }
        } else rawReports
        if (reports.isEmpty()) return DotColor.GRAY
        val top = recommender.rank(reports, fp, topK = 1).firstOrNull() ?: return DotColor.GRAY
        return when (top.bucket) {
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
    }
}

data class LibraryState(
    val loading: Boolean = true,
    val games: List<Pair<ScannedGame, DotColor>> = emptyList(),
    val fingerprint: DeviceFingerprint? = null,
    val romPicked: Boolean = false,
    val pcPicked: Boolean = false,
    val error: String? = null,
)

enum class DotColor { GREEN, YELLOW, RED, GRAY }
