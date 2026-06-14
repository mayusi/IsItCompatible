package io.github.mayusi.isitcompatible.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.BaseFixes
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.GuideResolver
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "It didn't work" interactive troubleshooter for one game.
 *
 * Flow: pick a symptom -> walk an ORDERED list of fixes one at a time with a
 * "did that work?" retest loop -> success (mark working / import config) or an
 * honest "out of known fixes" end state.
 *
 * Ranked fix list = (a) this game's own `guide.troubleshooting` entries that are
 * relevant to the chosen symptom FIRST, then (b) emulator-level fallback fixes
 * for the recommended emulator from [BaseFixes] (base-fixes.json).
 *
 * The YES / "mark working" / "import config" OUTCOMES are NOT handled here — the
 * screen wires them to the existing [GameDetailViewModel] hooks (saveJournal,
 * openJournalForm, importGameNativeConfig, applyGameNativeConfig) so the import
 * + journal logic is never duplicated.
 */
@HiltViewModel
class TroubleshootViewModel @Inject constructor(
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val guideResolver: GuideResolver,
    private val baseFixes: BaseFixes,
    private val prefs: UserPreferences,
    private val compatDb: CompatDbRepository,
    private val journalDao: JournalDao,
    private val driverDao: DriverDao,
    handle: SavedStateHandle,
) : ViewModel() {

    private val gameId: String = handle["gameId"] ?: error("gameId required")
    private val recommender = Recommender()

    private val _state = MutableStateFlow(TroubleshootState())
    val state: StateFlow<TroubleshootState> = _state.asStateFlow()

    /** Recommended emulator for this game; the base-fixes lookup keys off it. */
    private var recommendedEmulatorId: String? = null
    private var isWindows: Boolean = false

    init { resolveContext() }

    private fun resolveContext() {
        viewModelScope.launch {
            compatDb.ready.first { it }
            val game = gameDao.byId(gameId)
            isWindows = game?.platform.equals("WINDOWS", ignoreCase = true)
            val fp = prefs.data.first().fingerprint
            val rawReports = reportDao.byGame(gameId)
            // Policy: Windows games are GameNative-only.
            val reports = if (isWindows) {
                rawReports.filter { it.emulatorId.equals("gamenative", ignoreCase = true) }
            } else rawReports
            // Same emulator the detail screen recommends: top of the source-aware
            // ranking (real first, then generated). The troubleshooter's
            // emulator-level fallback fixes key off this id.
            recommendedEmulatorId = if (fp != null) {
                recommender.rankBySource(reports, fp, topK = 1).all().firstOrNull()?.emulatorId
            } else null
            _state.update { it.copy(isWindowsGame = isWindows) }
        }
    }

    /** User picked a symptom — build the ordered fix list and show fix #0. */
    fun pickSymptom(symptom: TroubleshootSymptom) {
        // Feature A: "It worked before" takes its own investigation path instead of
        // the normal fix-list flow. The outcome is surfaced as ROLLBACK_LOOKUP in
        // the state; the UI renders RollbackSection instead of FixStep.
        if (symptom == TroubleshootSymptom.WORKED_BEFORE) {
            _state.update {
                it.copy(
                    symptom = symptom,
                    fixes = emptyList(),
                    currentIndex = 0,
                    outcome = Outcome.ROLLBACK_LOOKUP,
                    rollback = null,
                )
            }
            lookUpRollback()
            return
        }
        viewModelScope.launch {
            val emuId = recommendedEmulatorId
            // (a) the game's own guide troubleshooting, relevant entries first.
            val guide = emuId?.let { runCatching { guideResolver.resolve(gameId, it) }.getOrNull() }
            val gameFixes: List<RankedFix> = guide?.troubleshooting
                ?.filter { symptom.matchesGuideEntry(it.symptom, it.fix) }
                ?.map { RankedFix(fix = it.symptom, detail = it.fix, fromGuide = true) }
                .orEmpty()

            // (b) emulator-level fallbacks for the recommended emulator + symptom.
            val emulatorFixes: List<RankedFix> = baseFixes.fixesFor(emuId, symptom.key)
                .map { RankedFix(fix = it.fix, detail = it.detail, fromGuide = false) }

            // Guide entries FIRST, then emulator-level fallbacks. De-dup by fix text
            // so a guide entry that restates a base fix doesn't show twice.
            val merged = (gameFixes + emulatorFixes)
                .distinctBy { it.fix.trim().lowercase() }

            _state.update {
                it.copy(
                    symptom = symptom,
                    fixes = merged,
                    currentIndex = 0,
                    outcome = if (merged.isEmpty()) Outcome.EXHAUSTED else Outcome.TRYING,
                )
            }
        }
    }

    /** "Yes it worked" — show the success state (the screen wires the actual log/import). */
    fun markWorked() {
        _state.update { it.copy(outcome = Outcome.SOLVED) }
    }

    /** "No, didn't work" — advance to the next ranked fix, or the exhausted end state. */
    fun nextFix() {
        _state.update {
            val next = it.currentIndex + 1
            if (next >= it.fixes.size) it.copy(outcome = Outcome.EXHAUSTED)
            else it.copy(currentIndex = next, outcome = Outcome.TRYING)
        }
    }

    /** Go back to the symptom picker (e.g. wrong symptom chosen). */
    fun reset() {
        _state.update { TroubleshootState(isWindowsGame = isWindows) }
    }

    /**
     * Feature A: "It worked before, now it doesn't" — look up journal history
     * for this game to find the last PERFECT or PLAYABLE entry, compare its
     * driver to the current driver for that family, and build honest rollback
     * guidance. Only claims a regression when journal evidence supports it.
     */
    fun lookUpRollback() {
        viewModelScope.launch {
            _state.update { it.copy(rollback = RollbackState.Loading) }
            val entries = journalDao.forGame(gameId)
            // Find the last working run (PERFECT or PLAYABLE), newest-first
            val lastWorking = entries.firstOrNull {
                it.stability.uppercase() in listOf("PERFECT", "PLAYABLE")
            }
            if (lastWorking == null) {
                _state.update {
                    it.copy(
                        rollback = RollbackState.NoEvidence(
                            "No previous working run recorded for this game. " +
                                "Log a run when you get it working — this section will improve over time.",
                        ),
                    )
                }
                return@launch
            }

            val workingDriverId = lastWorking.driverIdAtTimeOfRun
            if (workingDriverId == null) {
                _state.update {
                    it.copy(
                        rollback = RollbackState.NoEvidence(
                            "Your last working run (${formatRollbackDate(lastWorking.createdAt)}) " +
                                "didn't record which driver was active, so we can't compare versions. " +
                                "Log future runs with the driver applied to enable this check.",
                        ),
                    )
                }
                return@launch
            }

            val workingDriver = driverDao.byId(workingDriverId)
            // Look up ALL drivers to find what's currently available for the same GPU
            val allDrivers = driverDao.all()
            val currentDriver: DriverEntity? = if (workingDriver != null) {
                // Find a newer driver in the same family (same gpuTargets overlap)
                allDrivers
                    .filter { it.id != workingDriverId }
                    .filter { d ->
                        d.gpuTargets.split('|').any { t ->
                            workingDriver.gpuTargets.split('|').any { w ->
                                t.trim().equals(w.trim(), ignoreCase = true)
                            }
                        }
                    }
                    .maxByOrNull { it.dataAsOf }
            } else null

            val workingDriverName = workingDriver?.name ?: workingDriverId
            val currentDriverName = currentDriver?.name

            if (currentDriver == null || currentDriverName == workingDriverName ||
                currentDriverName.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        rollback = RollbackState.SameDriver(
                            lastWorkingDate = formatRollbackDate(lastWorking.createdAt),
                            driverName = workingDriverName,
                        ),
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    rollback = RollbackState.Regression(
                        lastWorkingDate = formatRollbackDate(lastWorking.createdAt),
                        workingDriverName = workingDriverName,
                        workingDriverUrl = workingDriver?.downloadUrl,
                        currentDriverName = currentDriverName,
                        workingEmulatorId = lastWorking.emulatorId,
                        workingPresetId = lastWorking.presetId,
                    ),
                )
            }
        }
    }

    private fun formatRollbackDate(epochMs: Long): String {
        val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        return fmt.format(java.util.Date(epochMs))
    }
}

/** Immutable UI state for the troubleshooter flow. */
data class TroubleshootState(
    /** Chosen symptom, or null while the picker is showing. */
    val symptom: TroubleshootSymptom? = null,
    /** Ordered fix list for the chosen symptom (guide entries first, then base fixes). */
    val fixes: List<RankedFix> = emptyList(),
    /** Index of the fix currently being shown. */
    val currentIndex: Int = 0,
    /** Where we are in the loop. */
    val outcome: Outcome = Outcome.PICKING,
    /** True for Windows/GameNative games — unlocks the "import working config" success CTA. */
    val isWindowsGame: Boolean = false,
    /** Feature A: rollback analysis result, null = not yet requested. */
    val rollback: RollbackState? = null,
) {
    val currentFix: RankedFix? get() = fixes.getOrNull(currentIndex)
    val stepLabel: String get() = "Fix ${currentIndex + 1} of ${fixes.size}"
}

/**
 * Feature A: result of comparing the last-working-run driver to the current driver.
 *
 * Only surfaces a regression claim when journal evidence (a PERFECT/PLAYABLE entry
 * with a recorded driverIdAtTimeOfRun) backs it up.
 */
sealed interface RollbackState {
    /** Lookup in progress. */
    data object Loading : RollbackState

    /** A driver regression is the likely cause — real evidence. */
    data class Regression(
        val lastWorkingDate: String,
        val workingDriverName: String,
        val workingDriverUrl: String?,
        val currentDriverName: String,
        val workingEmulatorId: String?,
        val workingPresetId: String?,
    ) : RollbackState

    /** Evidence says they're on the same driver → not a driver regression. */
    data class SameDriver(
        val lastWorkingDate: String,
        val driverName: String,
    ) : RollbackState

    /** No working run recorded, or no driver was captured. Honest "can't tell" state. */
    data class NoEvidence(val message: String) : RollbackState
}

/** One step in the ranked fix list, tagged with where it came from. */
data class RankedFix(
    val fix: String,
    val detail: String,
    /** True = from this game's guide.troubleshooting; false = emulator-level fallback. */
    val fromGuide: Boolean,
)

enum class Outcome {
    /** Symptom picker is showing. */
    PICKING,
    /** Showing a fix, awaiting "did it work?". */
    TRYING,
    /** User said yes — show the success / "mark working" state. */
    SOLVED,
    /** Ran out of ranked fixes — honest end state. */
    EXHAUSTED,
    /** Feature A: looking up journal+driver history for "worked before" regression analysis. */
    ROLLBACK_LOOKUP,
}

/**
 * The symptom list shown to the user. `key` matches the symptom keys in
 * base-fixes.json. [matchesGuideEntry] decides which of a game's free-text
 * `guide.troubleshooting` entries are relevant to this symptom (keyword match
 * over the entry's symptom + fix text); when nothing matches we still fall back
 * to the emulator-level base fixes.
 */
enum class TroubleshootSymptom(
    val key: String,
    val label: String,
    private val keywords: List<String>,
) {
    CRASH_ON_LAUNCH(
        "crash-on-launch",
        "Crashes on launch",
        listOf("crash", "launch", "start", "boot", "open", "won't run", "wont run", "closes", "exit"),
    ),
    BLACK_SCREEN(
        "black-screen",
        "Black screen",
        listOf("black screen", "blank", "no image", "nothing", "white screen", "frozen", "hang"),
    ),
    CUTSCENE_CRASH(
        "cutscene-crash",
        "Crashes at a cutscene / specific moment",
        listOf("cutscene", "cut scene", "fmv", "video", "movie", "wmv", "intro", "specific", "level", "scene"),
    ),
    NO_AUDIO(
        "no-audio",
        "No audio",
        listOf("audio", "sound", "no sound", "silent", "music", "voice"),
    ),
    LOW_FPS(
        "low-fps",
        "Bad / low FPS",
        listOf("fps", "slow", "lag", "stutter", "performance", "speed", "framerate", "frame rate", "choppy"),
    ),
    WONT_INSTALL(
        "wont-install",
        "Won't install / import",
        listOf("install", "import", "setup", "installer", "copy", "extract", "missing file"),
    ),
    WORKED_BEFORE(
        "worked-before",
        "It worked before, now it doesn't",
        listOf("worked", "regression", "update", "broke", "broken", "used to", "stopped", "no longer"),
    );

    /** Does this game's free-text troubleshooting entry relate to this symptom? */
    fun matchesGuideEntry(symptomText: String, fixText: String): Boolean {
        val hay = "$symptomText $fixText".lowercase()
        return keywords.any { hay.contains(it) }
    }

    companion object {
        /** The picker order. */
        val ordered: List<TroubleshootSymptom> = entries.toList()
    }
}
