package io.github.mayusi.isitcompatible.compatdb.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<GameEntity>)

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE platform = :platform")
    suspend fun byPlatform(platform: String): List<GameEntity>

    @Query("SELECT * FROM games")
    suspend fun all(): List<GameEntity>

    @Query("SELECT * FROM games WHERE titleSlug LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' LIMIT :limit")
    suspend fun search(q: String, limit: Int = 100): List<GameEntity>

    /**
     * v0.7 Chunk 7.4: rows still missing a cover. The IGDB sync worker walks
     * these in batches once a day and fills them in. Caller chunks by ~200 so
     * each IGDB roundtrip is reasonable; the worker also rate-limits to 4/sec
     * to stay under IGDB's free-tier limit.
     */
    @Query("SELECT * FROM games WHERE coverUrl IS NULL OR coverUrl = ''")
    suspend fun gamesMissingCover(): List<GameEntity>

    @Query("UPDATE games SET coverUrl = :url WHERE id = :id")
    suspend fun setCoverUrl(id: String, url: String)

    @Query("SELECT COUNT(*) FROM games")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM games")
    suspend fun deleteAll()

    /**
     * IIC round-trip: look up a WINDOWS game by its Steam app id so the
     * [SessionResultReceiver] can map the fork's numeric appId to our
     * stable [GameEntity.id].
     */
    @Query("SELECT * FROM games WHERE steamAppId = :steamAppId LIMIT 1")
    suspend fun bySteamAppId(steamAppId: Int): GameEntity?
}

@Dao
interface EmulatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<EmulatorEntity>)

    @Query("SELECT * FROM emulators WHERE id = :id")
    suspend fun byId(id: String): EmulatorEntity?

    @Query("SELECT * FROM emulators")
    suspend fun all(): List<EmulatorEntity>

    @Query("DELETE FROM emulators")
    suspend fun deleteAll()
}

@Dao
interface PresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PresetEntity>)

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun byId(id: String): PresetEntity?

    @Query("SELECT * FROM presets WHERE emulatorId = :emulatorId")
    suspend fun byEmulator(emulatorId: String): List<PresetEntity>

    @Query("DELETE FROM presets")
    suspend fun deleteAll()
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ReportEntity>)

    @Query("SELECT * FROM reports WHERE gameId = :gameId")
    suspend fun byGame(gameId: String): List<ReportEntity>

    /**
     * v0.7 Chunk 7.3: batched lookup so the Browse list can build its summary
     * in O(1) SQL roundtrips instead of O(games). Callers group the result by
     * gameId themselves — Room only returns a flat list.
     *
     * SQLite limits parameterised IN-clauses to ~999 bindings; the caller
     * batches if the input is bigger.
     */
    @Query("SELECT * FROM reports WHERE gameId IN (:gameIds)")
    suspend fun byGameIds(gameIds: List<String>): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM reports")
    fun countFlow(): Flow<Int>

    /**
     * v0.6: per-source report counts for the Updates screen breakdown.
     * Returns rows of (source, count) so we can show "X bundled · Y our GitHub · Z heuristic".
     */
    @Query("SELECT source AS source, COUNT(*) AS count FROM reports GROUP BY source")
    fun sourceBreakdownFlow(): Flow<List<SourceCount>>

    @Query("DELETE FROM reports")
    suspend fun deleteAll()
}

/** Row shape for [ReportDao.sourceBreakdownFlow]. */
data class SourceCount(val source: String, val count: Int)

/**
 * v0.5: the user's local-only run log.
 *
 * Never wiped by [CompatDbWriteDao.replaceAll] — sync only touches the
 * community tables. Journal entries survive any DB refresh.
 */
@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("SELECT * FROM journal_entries WHERE gameId = :gameId ORDER BY createdAt DESC")
    suspend fun forGame(gameId: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT DISTINCT gameId FROM journal_entries")
    fun observeTriedGameIds(): Flow<List<String>>

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    suspend fun all(): List<JournalEntryEntity>

    @Query("SELECT COUNT(*) FROM journal_entries")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * v0.8: guide reads + checklist-progress writes.
 *
 * Guide rows are part of the synced community DB (wiped + replaced by
 * [CompatDbWriteDao.replaceAll] like games/reports). guide_progress is
 * local-only — never touched by sync.
 */
@Dao
interface GuideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<GuideEntity>)

    /** All guides for this game on this emulator, plus the emulator base guide. */
    @Query("""
        SELECT * FROM guides
        WHERE emulatorId = :emulatorId
          AND (gameId = :gameId OR gameId IS NULL)
        ORDER BY tier ASC
    """)
    suspend fun candidatesFor(gameId: String, emulatorId: String): List<GuideEntity>

    /** Just the base (Tier-4, gameId NULL) guide for an emulator. */
    @Query("SELECT * FROM guides WHERE emulatorId = :emulatorId AND gameId IS NULL ORDER BY tier ASC LIMIT 1")
    suspend fun baseGuideFor(emulatorId: String): GuideEntity?

    @Query("SELECT COUNT(*) FROM guides")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM guides")
    suspend fun deleteAll()

    // ---- checklist progress (local-only) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setProgress(row: GuideProgressEntity)

    @Query("SELECT * FROM guide_progress WHERE guideKey = :guideKey")
    suspend fun progressFor(guideKey: String): List<GuideProgressEntity>

    @Query("SELECT * FROM guide_progress")
    suspend fun allProgress(): List<GuideProgressEntity>

    @Query("DELETE FROM guide_progress WHERE guideKey = :guideKey")
    suspend fun clearProgress(guideKey: String)

    /**
     * Feature B: return the set of guideKeys that have at least one done step
     * but are NOT fully completed. "Fully completed" is defined as every step
     * index 0..(totalSteps-1) being done — we check this in the VM after fetching
     * the step counts from the matching [GuideEntity].
     *
     * Returns all guide_progress rows so the VM can group + check per guideKey.
     * Avoids a complex correlated subquery that would be hard to test and maintain.
     */
    @Query("SELECT * FROM guide_progress WHERE done = 1")
    suspend fun allDoneSteps(): List<GuideProgressEntity>
}

/**
 * Chunk 4: the user's own imported, verified GameNative configs.
 *
 * Local-only and DURABLE — never wiped by [CompatDbWriteDao.replaceAll]. The
 * write transaction reads every row here and re-inserts it into `guides` as a
 * tier-1 guide AFTER the seed reload, so a verified config survives cold starts
 * and always wins over the bundled seed (resolver picks lowest tier).
 */
@Dao
interface LocalVerifiedGuideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LocalVerifiedGuideEntity)

    @Query("SELECT * FROM local_verified_guides")
    suspend fun all(): List<LocalVerifiedGuideEntity>

    @Query("SELECT * FROM local_verified_guides WHERE gameId = :gameId AND emulatorId = :emulatorId LIMIT 1")
    suspend fun forGameEmulator(gameId: String, emulatorId: String): LocalVerifiedGuideEntity?

    @Query("DELETE FROM local_verified_guides WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DriverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DriverEntity>)

    @Query("SELECT * FROM drivers WHERE id = :id")
    suspend fun byId(id: String): DriverEntity?

    @Query("SELECT * FROM drivers")
    suspend fun all(): List<DriverEntity>

    @Query("UPDATE drivers SET installedLocally = :installed WHERE id = :id")
    suspend fun setInstalled(id: String, installed: Boolean)

    /**
     * v0.6: stamp a row with the upstream-latest release tag and refresh the
     * dataAsOf timestamp. Touched by [DriverSyncWorker] after a successful
     * release-API roundtrip.
     */
    @Query("UPDATE drivers SET upstreamLatestTag = :tag, dataAsOf = :asOf WHERE id = :id")
    suspend fun setUpstreamLatest(id: String, tag: String?, asOf: Long)

    @Query("DELETE FROM drivers")
    suspend fun deleteAll()
}

/**
 * Feature B: favorites / watchlist DAO.
 *
 * Local-only, durable — [CompatDbWriteDao.replaceAll] never touches this table.
 * Used by [CompatDbSyncWorker] extension to diff compatibility state for alert notifications.
 */
@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE gameId = :gameId")
    suspend fun removeByGameId(gameId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE gameId = :gameId)")
    suspend fun isFavorite(gameId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE gameId = :gameId)")
    fun isFavoriteFlow(gameId: String): Flow<Boolean>

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Query("UPDATE favorites SET lastKnownBestState = :state WHERE gameId = :gameId")
    suspend fun updateLastKnownBestState(gameId: String, state: String)
}

/** Aggregate ops that run as one transaction so partial sync failures don't tear the DB. */
@Dao
interface CompatDbWriteDao {
    @Transaction
    suspend fun replaceAll(
        games: List<GameEntity>,
        emulators: List<EmulatorEntity>,
        presets: List<PresetEntity>,
        reports: List<ReportEntity>,
        drivers: List<DriverEntity>,
        guides: List<GuideEntity>,
        gameDao: GameDao,
        emulatorDao: EmulatorDao,
        presetDao: PresetDao,
        reportDao: ReportDao,
        driverDao: DriverDao,
        guideDao: GuideDao,
        localVerifiedGuideDao: LocalVerifiedGuideDao,
    ) {
        gameDao.deleteAll()
        emulatorDao.deleteAll()
        presetDao.deleteAll()
        reportDao.deleteAll()
        driverDao.deleteAll()
        guideDao.deleteAll()
        gameDao.upsertAll(games)
        emulatorDao.upsertAll(emulators)
        presetDao.upsertAll(presets)
        reportDao.upsertAll(reports)
        driverDao.upsertAll(drivers)
        guideDao.upsertAll(guides)
        // Chunk 4 (make-or-break): the seed reload above just wiped + replaced the
        // `guides` table, which would erase any user-imported verified config. Re-
        // apply every durable local verified guide on top, as tier 1, in the SAME
        // transaction. Done last so it overrides any same-id seed guide, and the
        // resolver (lowest tier wins) then surfaces it over the bundled data.
        val localGuides = localVerifiedGuideDao.all().map { it.toGuideEntity() }
        if (localGuides.isNotEmpty()) guideDao.upsertAll(localGuides)
    }
}
