package io.github.mayusi.isitcompatible.compatdb.backup

import kotlinx.serialization.Serializable

/**
 * Top-level versioned backup envelope.
 *
 * Shape:
 * ```json
 * {
 *   "version": 1,
 *   "exportedAt": 1718300000000,
 *   "journalEntries": [...],
 *   "verifiedGuides": [...],
 *   "favorites": [...],
 *   "guideProgress": [...]
 * }
 * ```
 *
 * ONLY the user's personal/local data is exported — NOT the bundled catalog
 * (games, emulators, presets, reports, drivers). The catalog is always
 * re-downloaded from the seed; there is no need to back it up and including
 * it would bloat the file by megabytes.
 *
 * [version] is bumped when the shape changes in a breaking way so the
 * importer can reject files it cannot handle.
 */
@Serializable
data class BackupEnvelope(
    /** Schema version. Currently 1. */
    val version: Int = CURRENT_VERSION,
    /** Epoch ms when this backup was created. */
    val exportedAt: Long,
    val journalEntries: List<BackupJournalEntry> = emptyList(),
    val verifiedGuides: List<BackupVerifiedGuide> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    val guideProgress: List<BackupGuideProgress> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        /** Highest version we know how to import. */
        const val MAX_SUPPORTED_VERSION = 1
    }
}

/** Mirrors [JournalEntryEntity] exactly for stable serialization. */
@Serializable
data class BackupJournalEntry(
    val id: String,
    val gameId: String,
    val emulatorId: String? = null,
    val presetId: String? = null,
    val avgFps: Int? = null,
    val stability: String,
    val notes: String? = null,
    val createdAt: Long,
    val sessionMinutes: Int? = null,
    val peakTempC: Int? = null,
    val driverIdAtTimeOfRun: String? = null,
    val shareWithCommunity: Boolean = false,
)

/** Mirrors [LocalVerifiedGuideEntity] exactly for stable serialization. */
@Serializable
data class BackupVerifiedGuide(
    val id: String,
    val gameId: String,
    val emulatorId: String,
    val sourceLabel: String? = null,
    val dataAsOf: Long,
    val stepsJson: String,
    val gameNativeConfigJson: String,
    val createdAt: Long,
)

/** Mirrors [FavoriteEntity] exactly for stable serialization. */
@Serializable
data class BackupFavorite(
    val id: String,
    val gameId: String,
    val createdAt: Long,
    val lastKnownBestState: String = "",
)

/** Mirrors [GuideProgressEntity] exactly for stable serialization. */
@Serializable
data class BackupGuideProgress(
    val guideKey: String,
    val stepIndex: Int,
    val done: Boolean,
)
