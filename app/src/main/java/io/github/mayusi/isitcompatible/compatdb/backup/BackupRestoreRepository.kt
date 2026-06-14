package io.github.mayusi.isitcompatible.compatdb.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.compatJson
import io.github.mayusi.isitcompatible.compatdb.room.FavoriteDao
import io.github.mayusi.isitcompatible.compatdb.room.FavoriteEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideDao
import io.github.mayusi.isitcompatible.compatdb.room.GuideProgressEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideDao
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export and import the user's personal local-only data as a versioned JSON
 * backup file chosen via the Storage Access Framework.
 *
 * What is backed up:
 *  - journal_entries  (run log — personal test results)
 *  - local_verified_guides  (imported GameNative configs — personal setups)
 *  - favorites  (watchlist)
 *  - guide_progress  (per-step checklist ticks)
 *
 * What is NOT backed up:
 *  - games / emulators / presets / reports / drivers — these are the community
 *    catalog, re-seeded from assets on every launch. Backing them up would add
 *    megabytes with zero benefit.
 *
 * Import strategy: MERGE (upsert). Existing rows with the same primary key are
 * overwritten by the backup's version, which is what "restore" means — the
 * backup is authoritative. Rows that exist locally but are NOT in the backup are
 * left untouched (we never wipe and replace).
 */
@Singleton
class BackupRestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalDao: JournalDao,
    private val localVerifiedGuideDao: LocalVerifiedGuideDao,
    private val favoriteDao: FavoriteDao,
    private val guideDao: GuideDao,
) {

    companion object {
        private const val TAG = "BackupRestoreRepo"
    }

    /**
     * Read all personal data from Room and write a versioned JSON backup to the
     * SAF [uri] the user chose via [Intent.ACTION_CREATE_DOCUMENT].
     *
     * All DB and IO work runs on [Dispatchers.IO]. Never call on the main thread.
     *
     * @return [ExportResult.Success] with item counts, or [ExportResult.Failure] with a reason.
     */
    suspend fun export(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val journal = journalDao.all()
            val guides = localVerifiedGuideDao.all()
            val favorites = favoriteDao.all()
            val progress = guideDao.allProgress()

            val envelope = BackupEnvelope(
                exportedAt = System.currentTimeMillis(),
                journalEntries = journal.map { it.toBackup() },
                verifiedGuides = guides.map { it.toBackup() },
                favorites = favorites.map { it.toBackup() },
                guideProgress = progress.map { it.toBackup() },
            )

            val json = compatJson.encodeToString(BackupEnvelope.serializer(), envelope)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext ExportResult.Failure("Could not open the file for writing.")

            Log.i(TAG, "Exported backup: ${journal.size} journal, ${guides.size} guides, " +
                "${favorites.size} favorites, ${progress.size} progress rows")

            ExportResult.Success(
                journalCount = journal.size,
                guideCount = guides.size,
                favoriteCount = favorites.size,
                progressCount = progress.size,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult.Failure("Export failed: ${e.message}")
        }
    }

    /**
     * Read a backup file from the SAF [uri] chosen via [Intent.ACTION_OPEN_DOCUMENT],
     * validate its structure, and merge-upsert all entries into Room.
     *
     * Merge strategy: upsert (INSERT OR REPLACE). Rows already present locally with
     * the same primary key are overwritten by the backup's version — this is correct
     * for a restore. Rows not present in the backup are left untouched.
     *
     * Validation:
     *  - File must be readable UTF-8 JSON.
     *  - Top-level must parse as [BackupEnvelope].
     *  - [BackupEnvelope.version] must be <= [BackupEnvelope.MAX_SUPPORTED_VERSION].
     *  - The "journalEntries", "favorites", etc. keys must be present (they have
     *    default empty-list values so an envelope with those fields simply has zero
     *    rows — valid and no-op for that table).
     *
     * A wrong/corrupt file gets a friendly message — we never crash.
     *
     * @return [ImportResult.Success] with counts, or [ImportResult.Failure] with a reason.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        // 1. Read file.
        val rawText = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read backup file", e)
            null
        }

        if (rawText.isNullOrBlank()) {
            return@withContext ImportResult.Failure(
                "Couldn't read that file. Make sure you picked an Is It Compatible backup (.json)."
            )
        }

        // 2. Parse.
        val envelope = try {
            compatJson.decodeFromString(BackupEnvelope.serializer(), rawText)
        } catch (e: Exception) {
            Log.w(TAG, "Backup parse failed", e)
            return@withContext ImportResult.Failure(
                "This doesn't look like an Is It Compatible backup. " +
                    "Pick the .json file exported from Settings > Backup & Restore."
            )
        }

        // 3. Version check.
        if (envelope.version > BackupEnvelope.MAX_SUPPORTED_VERSION) {
            return@withContext ImportResult.Failure(
                "This backup was made by a newer version of the app (schema v${envelope.version}). " +
                    "Update Is It Compatible to restore it."
            )
        }

        // 4. Upsert everything (off main thread, we're already on IO).
        try {
            envelope.journalEntries.forEach { journalDao.upsert(it.toEntity()) }
            envelope.verifiedGuides.forEach { guide ->
                val entity = guide.toEntity()
                localVerifiedGuideDao.upsert(entity)
                // Re-apply into `guides` immediately so the resolver sees it.
                guideDao.upsertAll(listOf(entity.toGuideEntity()))
            }
            envelope.favorites.forEach { favoriteDao.upsert(it.toEntity()) }
            envelope.guideProgress.forEach { guideDao.setProgress(it.toEntity()) }

            Log.i(TAG, "Imported backup v${envelope.version}: " +
                "${envelope.journalEntries.size} journal, " +
                "${envelope.verifiedGuides.size} guides, " +
                "${envelope.favorites.size} favorites, " +
                "${envelope.guideProgress.size} progress rows")

            ImportResult.Success(
                journalCount = envelope.journalEntries.size,
                guideCount = envelope.verifiedGuides.size,
                favoriteCount = envelope.favorites.size,
                progressCount = envelope.guideProgress.size,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import DB write failed", e)
            ImportResult.Failure("Couldn't save the backup data: ${e.message}")
        }
    }

    // ── Entity <-> Backup DTO conversions ────────────────────────────────────

    private fun JournalEntryEntity.toBackup() = BackupJournalEntry(
        id = id, gameId = gameId, emulatorId = emulatorId, presetId = presetId,
        avgFps = avgFps, stability = stability, notes = notes, createdAt = createdAt,
        sessionMinutes = sessionMinutes, peakTempC = peakTempC,
        driverIdAtTimeOfRun = driverIdAtTimeOfRun, shareWithCommunity = shareWithCommunity,
    )

    private fun BackupJournalEntry.toEntity() = JournalEntryEntity(
        id = id, gameId = gameId, emulatorId = emulatorId, presetId = presetId,
        avgFps = avgFps, stability = stability, notes = notes, createdAt = createdAt,
        sessionMinutes = sessionMinutes, peakTempC = peakTempC,
        driverIdAtTimeOfRun = driverIdAtTimeOfRun, shareWithCommunity = shareWithCommunity,
    )

    private fun LocalVerifiedGuideEntity.toBackup() = BackupVerifiedGuide(
        id = id, gameId = gameId, emulatorId = emulatorId, sourceLabel = sourceLabel,
        dataAsOf = dataAsOf, stepsJson = stepsJson,
        gameNativeConfigJson = gameNativeConfigJson, createdAt = createdAt,
    )

    private fun BackupVerifiedGuide.toEntity() = LocalVerifiedGuideEntity(
        id = id, gameId = gameId, emulatorId = emulatorId, sourceLabel = sourceLabel,
        dataAsOf = dataAsOf, stepsJson = stepsJson,
        gameNativeConfigJson = gameNativeConfigJson, createdAt = createdAt,
    )

    private fun FavoriteEntity.toBackup() = BackupFavorite(
        id = id, gameId = gameId, createdAt = createdAt,
        lastKnownBestState = lastKnownBestState,
    )

    private fun BackupFavorite.toEntity() = FavoriteEntity(
        id = id, gameId = gameId, createdAt = createdAt,
        lastKnownBestState = lastKnownBestState,
    )

    private fun GuideProgressEntity.toBackup() = BackupGuideProgress(
        guideKey = guideKey, stepIndex = stepIndex, done = done,
    )

    private fun BackupGuideProgress.toEntity() = GuideProgressEntity(
        guideKey = guideKey, stepIndex = stepIndex, done = done,
    )

    // ── Result types ─────────────────────────────────────────────────────────

    sealed interface ExportResult {
        data class Success(
            val journalCount: Int,
            val guideCount: Int,
            val favoriteCount: Int,
            val progressCount: Int,
        ) : ExportResult

        data class Failure(val reason: String) : ExportResult
    }

    sealed interface ImportResult {
        data class Success(
            val journalCount: Int,
            val guideCount: Int,
            val favoriteCount: Int,
            val progressCount: Int,
        ) : ImportResult

        data class Failure(val reason: String) : ImportResult
    }
}
