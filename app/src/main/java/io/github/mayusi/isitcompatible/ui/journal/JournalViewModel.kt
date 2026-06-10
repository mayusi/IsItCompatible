package io.github.mayusi.isitcompatible.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _state = MutableStateFlow(JournalState())
    val state: StateFlow<JournalState> = _state.asStateFlow()

    init {
        // Stream entries; hydrate game + emulator names on each change.
        journalDao.observeAll()
            .onEach { entries ->
                val gameIds = entries.map { it.gameId }.toSet()
                val emuIds = entries.mapNotNull { it.emulatorId }.toSet()
                val games = mutableMapOf<String, GameEntity>()
                gameIds.forEach { id -> gameDao.byId(id)?.let { games[id] = it } }
                val emusById = emulatorDao.all().associateBy { it.id }
                    .filterKeys { it in emuIds }
                val stats = computeStats(entries)
                _state.update {
                    it.copy(
                        loading = false,
                        entries = entries,
                        gamesById = games,
                        emulatorsById = emusById,
                        stats = stats,
                    )
                }
            }
            .launchIn(viewModelScope)
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

data class JournalState(
    val loading: Boolean = true,
    val entries: List<JournalEntryEntity> = emptyList(),
    val gamesById: Map<String, GameEntity> = emptyMap(),
    val emulatorsById: Map<String, EmulatorEntity> = emptyMap(),
    val stats: JournalStats? = null,
)
