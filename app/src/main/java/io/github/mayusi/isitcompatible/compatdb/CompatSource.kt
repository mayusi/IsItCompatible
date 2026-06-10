package io.github.mayusi.isitcompatible.compatdb

import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity

/**
 * Pluggable compatibility-data source.
 *
 * v1 implementations:
 *  - [GithubJsonCompatSource]  — our community-submitted JSON in GitHub
 *  - [EmuReadyCompatSource]    — no live fetch by design; EmuReady community data
 *                                is ingested at build time (tools/ingest) and ships
 *                                as bundled seed (EMUREADY_SNAPSHOT), loaded by
 *                                [BundledCompatSource]. See its KDoc.
 *  - [BundledCompatSource]     — seed data baked into the APK so day-1 sideload has reports
 */
interface CompatSource {
    val sourceTag: String  // "OUR_GITHUB", "EMUREADY_SNAPSHOT", "EMUREADY_LIVE", "BUNDLED"

    /** Returns the latest snapshot, or null if we couldn't reach the source. */
    suspend fun fetch(): CompatSnapshot?
}

data class CompatSnapshot(
    val games: List<GameEntity>,
    val emulators: List<EmulatorEntity>,
    val presets: List<PresetEntity>,
    val reports: List<ReportEntity>,
    val drivers: List<DriverEntity>,
    val guides: List<GuideEntity> = emptyList(),
)
