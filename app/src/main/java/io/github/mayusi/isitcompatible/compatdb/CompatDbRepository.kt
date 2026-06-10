package io.github.mayusi.isitcompatible.compatdb

import android.util.Log
import io.github.mayusi.isitcompatible.compatdb.room.CompatDb
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.compatdb.room.SourceCount
import io.github.mayusi.isitcompatible.data.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges all [CompatSource]s and writes the union into Room.
 *
 * Strategy: union-by-id with later-wins (sources are queried in priority
 * order, last write wins). Reports are merged additively — they never replace
 * one another since each report id is globally unique.
 */
@Singleton
class CompatDbRepository @Inject constructor(
    private val db: CompatDb,
    private val bundled: BundledCompatSource,
    private val ourGithub: GithubJsonCompatSource,
    private val emuReady: EmuReadyCompatSource,
    private val userPrefs: UserPreferences,
) {

    private val _ready = MutableStateFlow(false)
    /**
     * Becomes true after the first successful [sync]. Anything that depends on
     * having compatibility data in the DB (the Library scanner, Game Detail
     * screen, recommender) should wait on this before reading.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Pulls from every source and rewrites the local DB.
     *
     * @param remoteOnly if true, skips the bundled seed. Used by the manual
     *                   "refresh" button so the user can confirm a remote fetch worked.
     * @return summary of what was merged.
     */
    suspend fun sync(remoteOnly: Boolean = false): SyncResult {
        val snapshots = mutableListOf<Pair<String, CompatSnapshot>>()

        if (!remoteOnly) {
            bundled.fetch()?.let { snapshots += bundled.sourceTag to it }
        }
        // Order matters — remote sources override bundled seed entries with the same id.
        ourGithub.fetch()?.let { snapshots += ourGithub.sourceTag to it }
        emuReady.fetch()?.let { snapshots += emuReady.sourceTag to it }

        if (snapshots.isEmpty()) {
            Log.w(TAG, "No sources returned data; leaving local DB untouched")
            // Stamp the attempt so the Updates screen can show "tried at <date>"
            // even when nothing came back. remoteReached stays false.
            runCatching { userPrefs.setLastSync(System.currentTimeMillis(), remoteReached = false) }
            return SyncResult(0, 0, 0, 0, 0, emptyList(), remoteReached = false)
        }

        val games = LinkedHashMap<String, GameEntity>()
        val emulators = LinkedHashMap<String, EmulatorEntity>()
        val presets = LinkedHashMap<String, PresetEntity>()
        val reports = LinkedHashMap<String, ReportEntity>()
        val drivers = LinkedHashMap<String, DriverEntity>()
        val guides = LinkedHashMap<String, GuideEntity>()

        for ((_, snap) in snapshots) {
            snap.games.forEach { games[it.id] = it }
            snap.emulators.forEach { emulators[it.id] = it }
            snap.presets.forEach { presets[it.id] = it }
            snap.reports.forEach { reports[it.id] = it }
            snap.drivers.forEach { drivers[it.id] = it }
            snap.guides.forEach { guides[it.id] = it }
        }

        db.writeDao().replaceAll(
            games.values.toList(),
            emulators.values.toList(),
            presets.values.toList(),
            reports.values.toList(),
            drivers.values.toList(),
            guides.values.toList(),
            db.gameDao(), db.emulatorDao(), db.presetDao(), db.reportDao(), db.driverDao(), db.guideDao(),
            // Chunk 4: re-applies the user's durable imported verified guides on top
            // of the freshly-reloaded seed, inside the same transaction.
            db.localVerifiedGuideDao(),
        )

        _ready.value = true

        // True if any non-bundled source actually returned a snapshot. The bundled
        // tag is "BUNDLED" — anything else counts as a remote that came through.
        val remoteReached = snapshots.any { it.first != bundled.sourceTag }
        runCatching { userPrefs.setLastSync(System.currentTimeMillis(), remoteReached) }

        return SyncResult(
            gameCount = games.size,
            emulatorCount = emulators.size,
            presetCount = presets.size,
            reportCount = reports.size,
            driverCount = drivers.size,
            sourceTags = snapshots.map { it.first },
            remoteReached = remoteReached,
        )
    }

    fun gameCountFlow(): Flow<Int> = db.gameDao().countFlow()
    fun reportCountFlow(): Flow<Int> = db.reportDao().countFlow()
    /** v0.8: guide count for the Updates screen. */
    fun guideCountFlow(): Flow<Int> = db.guideDao().countFlow()
    /** v0.6: per-source report breakdown for the Updates screen. */
    fun reportSourceBreakdownFlow(): Flow<List<SourceCount>> =
        db.reportDao().sourceBreakdownFlow()
    fun summaryFlow(): Flow<Pair<Int, Int>> =
        combine(gameCountFlow(), reportCountFlow()) { g, r -> g to r }

    data class SyncResult(
        val gameCount: Int,
        val emulatorCount: Int,
        val presetCount: Int,
        val reportCount: Int,
        val driverCount: Int,
        val sourceTags: List<String>,
        /** v0.6: true if at least one non-bundled source returned data. */
        val remoteReached: Boolean = false,
    )

    companion object { private const val TAG = "CompatDbRepository" }
}
