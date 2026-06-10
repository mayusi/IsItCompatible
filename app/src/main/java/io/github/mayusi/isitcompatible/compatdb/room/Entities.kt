package io.github.mayusi.isitcompatible.compatdb.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Game entity. The id is a stable hash of `platform|titleSlug` so the same
 * game from different sources collapses into one row.
 */
@Entity(
    tableName = "games",
    indices = [Index("titleSlug"), Index("platform")],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleSlug: String,
    val platform: String,        // "PS2", "WINDOWS", "SWITCH", ...
    val releaseYear: Int?,
    val region: String?,
    val coverUrl: String?,
    /** Pipe-separated lowercase exe names (Windows games only) for the scanner to match. */
    val exeHints: String?,
    /** Pipe-separated lowercase ROM filename substrings for matching. */
    val romHints: String?,
    /**
     * Cross-platform availability — pipe-separated platform tags this title
     * also exists on. Example: a PS2 game ported to PC + Switch reads
     * "PS2|WINDOWS|SWITCH". The UI uses this to show "also available on…"
     * with deep links to those entries (which live as separate rows).
     */
    val alsoOn: String? = null,
    /** Free-text description shown in the header. */
    val description: String? = null,
    /** Genre tags, pipe-separated. */
    val genres: String? = null,
    /**
     * Numbered setup steps (newline-separated). Shown as a numbered timeline
     * on the Game Detail screen. Pure text; markdown not interpreted.
     */
    val setupSteps: String? = null,
    /**
     * Known issues / workarounds (newline-separated bullets). Rendered as
     * warning cards on the Game Detail screen.
     */
    val knownIssues: String? = null,
    /**
     * Recommended device RAM in GB for the best experience. Surfaces in the
     * "Hardware requirements" callout next to per-preset VRAM.
     */
    val recommendedRamGb: Int? = null,
    /**
     * Best-version guidance. Plain text recommending which port to emulate
     * if the user has options ("PC port via GameNative is your best bet; PS3
     * version is barely playable").
     */
    val bestVersionGuidance: String? = null,
    /**
     * v0.4: Pipe-separated screenshot URLs (max 4) sourced from IGDB or
     * hand-curated. Rendered as a horizontal scroll under the platform header.
     */
    val screenshotUrls: String? = null,
    /**
     * v0.4: Newline-separated bullets describing required BIOS/firmware/keys
     * (e.g. "PS2 BIOS — SCPH-70012.bin or regional equivalent — dump from
     * your own console.") Rendered as a red-accent Section.
     */
    val biosRequirements: String? = null,
    /**
     * v0.4: Newline-separated bullets for community mods / patches required
     * or recommended (e.g. "Install SKSE matching your Skyrim build number.")
     * Rendered as a purple-accent Section, distinct from knownIssues so users
     * see them as proactive solutions instead of caveats.
     */
    val modsAndPatches: String? = null,
    /**
     * v0.4: JSON object as a string. Keys are emulator ids; values are
     * newline-separated setup steps specific to that emulator. The Game
     * Detail screen renders this as tabbed segments — one tab per emulator.
     * When null OR a particular emulator id isn't present, the UI falls back
     * to the generic preset-derived steps.
     *
     * Shape (after json-parse):
     * ```
     * {
     *   "winlator-cmod": "Install Cmod\nInstall Turnip 24.3...",
     *   "gamenative":     "Install GameNative\nLoad Proton-GE..."
     * }
     * ```
     */
    val perEmulatorSetup: String? = null,
    /**
     * Optional store/Steam app id (e.g. DMC HD Collection = 631510). Drives the
     * GameNative one-tap "Apply config & launch" handoff, which passes this as the
     * `app_id` intent extra. Nullable — games without it don't show the one-tap button.
     */
    val steamAppId: Int? = null,
)

/** Emulator/translator entry — Winlator, NetherSX2-Patch, Eden, etc. */
@Entity(tableName = "emulators")
data class EmulatorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageId: String?,        // Android package id, null if unknown
    val platformTargets: String,   // pipe-separated: "WINDOWS" or "PS2|PS1"
    val sourceUrl: String?,
    val notes: String?,
)

/** A named, applyable bundle of settings + optional driver. */
@Entity(
    tableName = "presets",
    indices = [Index("emulatorId")],
)
data class PresetEntity(
    @PrimaryKey val id: String,
    val emulatorId: String,
    val name: String,
    /** JSON object describing settings — interpretation lives in the per-emulator template renderer. */
    val settingsJson: String,
    val driverId: String?,         // nullable — not all presets need a driver swap
    val notes: String?,
    /**
     * v0.6: epoch ms when this preset's information was last verified upstream.
     * Defaults to 0 (= never verified, use bundled date). UI displays
     * "preset info from <date>" + a "may be outdated" chip when older than
     * 60 days.
     */
    val dataAsOf: Long = 0L,
)

/** A single user report from EmuReady, our GitHub DB, or any future source. */
@Entity(
    tableName = "reports",
    indices = [Index("gameId"), Index("emulatorId"), Index("source")],
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val emulatorId: String,
    val presetId: String?,
    // Device fingerprint, denormalised — easier to query without joins
    val socFamily: String,
    val gpuVendor: String,         // GpuVendor.name
    val gpuModel: String,
    val ramMb: Int,
    val androidApi: Int,
    // Outcome
    val avgFps: Int?,
    val stability: String,         // PERFECT / PLAYABLE / GLITCHY / CRASHES
    val notes: String?,
    val source: String,            // EMUREADY_SNAPSHOT / EMUREADY_LIVE / OUR_GITHUB
    val sourceRef: String?,        // URL back to original report
    val submittedAt: Long,
)

/**
 * v0.5: the user's own log of what they actually ran on their device.
 *
 * Local-only — never synced anywhere. The recommender treats journal entries
 * as a "yours" source ranked above community real reports for the same
 * game-emulator-preset combo, because *your own past result on your own
 * device* is the strongest signal you can have.
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index("gameId"), Index("createdAt")],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,                  // UUID
    val gameId: String,
    val emulatorId: String?,
    val presetId: String?,
    val avgFps: Int?,                            // null = "didn't measure"
    val stability: String,                       // PERFECT / PLAYABLE / GLITCHY / CRASHES
    val notes: String?,
    val createdAt: Long,                         // epoch ms
    val sessionMinutes: Int?,                    // optional play-session length
    val peakTempC: Int?,                         // optional thermal observation
    /** Captures the driver that was applied at the time (history-proof — drivers move on). */
    val driverIdAtTimeOfRun: String?,
    /**
     * v0.6: opt-in flag that lets the user push this entry to the community DB
     * via a pre-filled GitHub Issue. Default false — the journal stays local
     * unless the user explicitly ticks the toggle on the form.
     *
     * When true, a one-shot Intent.ACTION_VIEW opens the user's browser to a
     * pre-filled issue template right after save. We never POST silently —
     * the user gets to review the title/body and clicks "Submit issue" themselves.
     */
    val shareWithCommunity: Boolean = false,
)

/**
 * v0.8: a setup guide for getting a game actually running.
 *
 * Guides are layered into four trust tiers; the [GuideResolver] picks the
 * lowest tier-number (= highest trust) available for a given (gameId, emulatorId),
 * falling back to the per-emulator base guide (gameId = null) when no
 * game-specific guide exists.
 *
 *  Tier 1 = Verified   — a real user ran it (community DB / journal share)
 *  Tier 2 = Authored   — hand-written against known-good configs
 *  Tier 3 = EmuReady    — imported from the EmuReady API, dated + backlinked
 *  Tier 4 = Base       — per-emulator default, always present, genuinely good
 *
 * [stepsJson] is a JSON array of typed steps (GET_APP / GET_DRIVER / CONTAINER /
 * FILES / BIOS / ACTION / TIP) so the UI can attach the right affordance
 * (download button, copy-path, apply-driver) to each line. That typing is the
 * whole point — it's the difference between *reading about* running a game and
 * *actually running it*.
 */
@Entity(
    tableName = "guides",
    indices = [Index("gameId"), Index("emulatorId")],
)
data class GuideEntity(
    @PrimaryKey val id: String,          // "<gameId|base>:<emulatorId>:t<tier>"
    /** null = per-emulator base guide (Tier 4); non-null = game-specific. */
    val gameId: String?,
    val emulatorId: String,
    /** 1 Verified · 2 Authored · 3 EmuReady · 4 Base. Lower = more trusted. */
    val tier: Int,
    /** Human label for the trust badge, e.g. "From EmuReady" or "3 people ran this". */
    val sourceLabel: String?,
    /** Backlink to the original source (Tier 3 EmuReady mostly). */
    val sourceUrl: String?,
    /** Epoch ms last verified — reuses the v0.6 "as of <date>" + "may be outdated" UI. */
    val dataAsOf: Long = 0L,
    /** JSON array of typed [io.github.mayusi.isitcompatible.compatdb.GuideStepDto]. */
    val stepsJson: String,
    /** JSON array of {symptom, fix} objects, or null if no troubleshooting authored. */
    val troubleshootingJson: String? = null,
    /**
     * Chunk 1: raw importable GameNative per-game config (`<Game>_config.json`),
     * stored opaquely as a compact JSON string. Null when the guide carries no
     * config. The apply layer writes this verbatim to the importable file.
     */
    val gameNativeConfigJson: String? = null,
)

/**
 * Chunk 4: a guide the USER built by importing their own working GameNative
 * config off their device. Stored in a DEDICATED table that the community-DB
 * sync ([CompatDbWriteDao.replaceAll]) never wipes — exactly like
 * [JournalEntryEntity]. This is the make-or-break correctness point: the
 * `guides` table IS wiped + re-seeded on every cold start, so a tier-1 guide
 * inserted straight into `guides` would vanish on the next launch. Instead we
 * persist the verified guide here, and [CompatDbWriteDao.replaceAll] re-applies
 * every row from this table back into `guides` (as tier 1) at the END of the
 * seed-reload transaction, so the user's verified config always survives a
 * restart AND always wins over the seed via the resolver's tier ordering.
 *
 * One row per (gameId, emulatorId) — re-importing a newer working config for the
 * same game replaces the previous verified guide (REPLACE on the same id).
 *
 * The columns mirror the subset of [GuideEntity] needed to rebuild the guide
 * row verbatim; [toGuideEntity] does the projection.
 */
@Entity(
    tableName = "local_verified_guides",
    indices = [Index("gameId"), Index("emulatorId")],
)
data class LocalVerifiedGuideEntity(
    /** Same id we use in `guides`: "local:<gameId>:<emulatorId>:t1". */
    @PrimaryKey val id: String,
    val gameId: String,
    val emulatorId: String,
    /** Human label e.g. "Verified on your device · Snapdragon 8 Elite · Adreno 830". */
    val sourceLabel: String?,
    /** Epoch ms the user imported this config. */
    val dataAsOf: Long,
    /** JSON array of typed GuideStepDto (GET_APP + ACTION import). */
    val stepsJson: String,
    /** The sanitized, reusable GameNative config JSON string. */
    val gameNativeConfigJson: String,
    /** Epoch ms this local guide row was created/updated. */
    val createdAt: Long,
) {
    /** Project into a Tier-1 [GuideEntity] for the `guides` table / resolver. */
    fun toGuideEntity(): GuideEntity = GuideEntity(
        id = id,
        gameId = gameId,
        emulatorId = emulatorId,
        tier = 1,
        sourceLabel = sourceLabel,
        sourceUrl = null,
        dataAsOf = dataAsOf,
        stepsJson = stepsJson,
        troubleshootingJson = null,
        gameNativeConfigJson = gameNativeConfigJson,
    )
}

/**
 * v0.8: per-step checklist progress. Local-only, never synced. Keyed by the
 * guide instance (gameId:emulatorId) so progress survives app restarts and is
 * scoped to the exact game+emulator the user is setting up.
 */
@Entity(
    tableName = "guide_progress",
    primaryKeys = ["guideKey", "stepIndex"],
)
data class GuideProgressEntity(
    val guideKey: String,                // "<gameId>:<emulatorId>"
    val stepIndex: Int,
    val done: Boolean,
)

/** A downloadable GPU driver (Adreno/Turnip variants). */
@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey val id: String,
    val name: String,
    val gpuTargets: String,        // pipe-separated GPU model names that fit
    val downloadUrl: String,
    val sha256: String?,
    val sizeBytes: Long?,
    /** True once the user has downloaded it via the apply flow. */
    val installedLocally: Boolean = false,
    /**
     * v0.6: epoch ms when this driver entry was last verified against the
     * upstream GitHub Releases. Updated by [DriverFetcher] / [DriverSyncWorker].
     */
    val dataAsOf: Long = 0L,
    /**
     * v0.6: when an upstream check finds a newer release, we set this to its
     * tag (e.g. "Turnip-v25.1.0_R1") so the UI can show "new version available".
     * null = no newer version known, or upstream check hasn't run.
     */
    val upstreamLatestTag: String? = null,
)
