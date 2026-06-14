package io.github.mayusi.isitcompatible.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.GuideResolver
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Journal tab. Subscribes to the journal flow so any new
 * entry added from Game Detail or the Apply sheet shows up immediately when
 * the user navigates over.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalDao: JournalDao,
    private val gameDao: GameDao,
    private val emulatorDao: EmulatorDao,
    private val driverDao: DriverDao,
    private val guideDao: GuideDao,
    private val guideResolver: GuideResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(JournalState())
    val state: StateFlow<JournalState> = _state.asStateFlow()

    init {
        // Stream entries; hydrate game + emulator + driver names on each change.
        journalDao.observeAll()
            .onEach { entries ->
                val gameIds = entries.map { it.gameId }.toSet()
                val emuIds = entries.mapNotNull { it.emulatorId }.toSet()
                val games = mutableMapOf<String, GameEntity>()
                gameIds.forEach { id -> gameDao.byId(id)?.let { games[id] = it } }
                val emusById = emulatorDao.all().associateBy { it.id }
                    .filterKeys { it in emuIds }
                // QW4: hydrate driver names so journal rows can show which driver was active
                val driverIds = entries.mapNotNull { it.driverIdAtTimeOfRun }.toSet()
                val driversById = driverIds.mapNotNull { driverDao.byId(it) }.associateBy { it.id }
                val stats = computeStats(entries)
                _state.update {
                    it.copy(
                        loading = false,
                        entries = entries,
                        gamesById = games,
                        emulatorsById = emusById,
                        driversById = driversById,
                        stats = stats,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Feature B: load in-progress guides on init; re-load whenever called.
        viewModelScope.launch { refreshInProgress() }
    }

    /**
     * Feature B: build the "in-progress setups" list.
     *
     * Strategy: query all done steps, group by guideKey, then for each guideKey
     * resolve the guide to get total step count. A guide is "in progress" when
     * doneCount >= 1 AND doneCount < totalSteps.
     *
     * guideKey format is "<gameId>:<emulatorId>" (set by GuideSection when user
     * checks a step).
     */
    suspend fun refreshInProgress() {
        val inProgress = withContext(Dispatchers.IO) {
            val doneRows = guideDao.allDoneSteps()
            if (doneRows.isEmpty()) return@withContext emptyList<InProgressSetup>()

            val byKey = doneRows.groupBy { it.guideKey }
            val result = mutableListOf<InProgressSetup>()
            for ((guideKey, doneSteps) in byKey) {
                val parts = guideKey.split(":")
                if (parts.size < 2) continue
                val gameId = parts[0]
                val emulatorId = parts.drop(1).joinToString(":")
                // Resolve the guide to get total step count
                val resolved = guideResolver.resolve(gameId, emulatorId) ?: continue
                val totalSteps = resolved.steps.size
                val doneCount = doneSteps.size
                // Must have at least 1 done but not all done
                if (doneCount < 1 || doneCount >= totalSteps) continue
                val game = gameDao.byId(gameId) ?: continue
                val emulatorName = emulatorDao.byId(emulatorId)?.name ?: emulatorId
                result.add(
                    InProgressSetup(
                        gameId = gameId,
                        emulatorId = emulatorId,
                        gameTitle = game.title,
                        emulatorName = emulatorName,
                        doneSteps = doneCount,
                        totalSteps = totalSteps,
                    )
                )
            }
            result.sortedBy { it.gameTitle }
        }
        _state.update { it.copy(inProgressSetups = inProgress) }
    }

    private fun computeStats(entries: List<JournalEntryEntity>): JournalStats {
        val gameCount = entries.map { it.gameId }.toSet().size
        val workingCount = entries.count {
            it.stability.uppercase() in listOf("PERFECT", "PLAYABLE")
        }
        val sessionHrs = entries.sumOf { it.sessionMinutes ?: 0 } / 60f
        return JournalStats(
            gameCount = gameCount,
            workingCount = workingCount,
            sessionHrs = sessionHrs,
        )
    }

    fun delete(entryId: String) {
        viewModelScope.launch { journalDao.delete(entryId) }
    }
}

data class JournalStats(
    val gameCount: Int,
    val workingCount: Int,
    val sessionHrs: Float,
)

/**
 * Feature B: a guide setup that the user has partially completed.
 * "In progress" = at least 1 step done, not all steps done.
 */
data class InProgressSetup(
    val gameId: String,
    val emulatorId: String,
    val gameTitle: String,
    val emulatorName: String,
    val doneSteps: Int,
    val totalSteps: Int,
)

data class JournalState(
    val loading: Boolean = true,
    val entries: List<JournalEntryEntity> = emptyList(),
    val gamesById: Map<String, GameEntity> = emptyMap(),
    val emulatorsById: Map<String, EmulatorEntity> = emptyMap(),
    /** QW4: drivers referenced by journal entries — for showing driver name at run time. */
    val driversById: Map<String, DriverEntity> = emptyMap(),
    val stats: JournalStats? = null,
    /**
     * Feature B: guides with 1+ done steps but not yet complete.
     * Null means not yet loaded. Empty list means none in progress.
     */
    val inProgressSetups: List<InProgressSetup>? = null,
)
