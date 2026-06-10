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

                val withDots = games.map { sg ->
                    sg to dotFor(sg, fp)
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

    private suspend fun dotFor(g: ScannedGame, fp: DeviceFingerprint?): DotColor {
        if (g.gameId == null || fp == null) return DotColor.GRAY
        val rawReports = reportDao.byGame(g.gameId)
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
