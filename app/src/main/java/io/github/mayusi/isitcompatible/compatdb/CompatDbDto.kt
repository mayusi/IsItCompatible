package io.github.mayusi.isitcompatible.compatdb

import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json

/**
 * JSON shape of the compatdb. Lives separately from Room entities so the
 * on-disk format can evolve without forcing a DB migration each time, and so
 * EmuReady's eventual JSON can be normalised through the same DTO layer.
 *
 * Example:
 * ```
 * {
 *   "schema": 1,
 *   "generatedAt": "2026-05-25T10:00:00Z",
 *   "games": [{ "id": "ps2:god-of-war", "title": "God of War", ... }],
 *   "emulators": [...],
 *   "presets": [...],
 *   "reports": [...],
 *   "drivers": [...]
 * }
 * ```
 */
@Serializable
data class CompatDbDto(
    val schema: Int = 1,
    val generatedAt: String? = null,
    val games: List<GameDto> = emptyList(),
    val emulators: List<EmulatorDto> = emptyList(),
    val presets: List<PresetDto> = emptyList(),
    val reports: List<ReportDto> = emptyList(),
    val drivers: List<DriverDto> = emptyList(),
    /** v0.8: layered setup guides. */
    val guides: List<GuideDto> = emptyList(),
)

@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val titleSlug: String,
    val platform: String,
    val releaseYear: Int? = null,
    val region: String? = null,
    val coverUrl: String? = null,
    val exeHints: List<String> = emptyList(),
    val romHints: List<String> = emptyList(),
    val alsoOn: List<String> = emptyList(),
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val setupSteps: List<String> = emptyList(),
    val knownIssues: List<String> = emptyList(),
    val recommendedRamGb: Int? = null,
    val bestVersionGuidance: String? = null,
    // v0.4: richer per-game fields
    val screenshotUrls: List<String> = emptyList(),
    val biosRequirements: List<String> = emptyList(),
    val modsAndPatches: List<String> = emptyList(),
    /** Keys = emulator id, values = list of setup-step lines for that emulator. */
    val perEmulatorSetup: Map<String, List<String>> = emptyMap(),
    /**
     * Optional store/Steam app id for this game (e.g. DMC HD Collection = 631510).
     * Used by the GameNative one-tap "Apply config & launch" handoff, which needs
     * the numeric app id to pass in the `app_id` intent extra. Nullable + back-
     * compatible: games without it simply don't get the one-tap button.
     */
    val steamAppId: Int? = null,
)

@Serializable
data class EmulatorDto(
    val id: String,
    val name: String,
    val packageId: String? = null,
    val platformTargets: List<String>,
    val sourceUrl: String? = null,
    val notes: String? = null,
)

@Serializable
data class PresetDto(
    val id: String,
    val emulatorId: String,
    val name: String,
    val settings: JsonElement,
    val driverId: String? = null,
    val notes: String? = null,
    /**
     * v0.6: ISO date like "2026-04-15" describing when the preset's settings
     * were last verified upstream. Stored in Room as epoch ms; null/missing
     * in JSON means "use bundled-snapshot date" which mapper fills in.
     */
    val dataAsOf: String? = null,
)

@Serializable
data class ReportDto(
    val id: String,
    val gameId: String,
    val emulatorId: String,
    val presetId: String? = null,
    @SerialName("device") val device: DeviceDto,
    val avgFps: Int? = null,
    val stability: String,
    val notes: String? = null,
    val source: String = "OUR_GITHUB",
    val sourceRef: String? = null,
    val submittedAt: Long = 0L,
)

@Serializable
data class DeviceDto(
    val socFamily: String,
    val gpuVendor: String,
    val gpuModel: String,
    val ramMb: Int,
    val androidApi: Int = 0,
)

@Serializable
data class DriverDto(
    val id: String,
    val name: String,
    val gpuTargets: List<String>,
    val downloadUrl: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    /** v0.6: ISO date last verified upstream (see PresetDto.dataAsOf). */
    val dataAsOf: String? = null,
    /** v0.6: GitHub release tag of a newer driver if upstream check found one. */
    val upstreamLatestTag: String? = null,
)

/**
 * v0.8: a setup guide in source JSON. `steps` and `troubleshooting` stay
 * structured here (readable + diffable) and the mapper serializes them to JSON
 * strings for Room storage.
 *
 * Shape:
 * ```
 * {
 *   "id": "base:gamenative:t4",
 *   "gameId": null,
 *   "emulatorId": "gamenative",
 *   "tier": 4,
 *   "sourceLabel": "Standard GameNative setup",
 *   "dataAsOf": "2026-05-27",
 *   "steps": [
 *     { "kind": "GET_APP", "text": "Install GameNative", "url": "https://..." },
 *     { "kind": "CONTAINER", "text": "Create a container", "settings": {"Wine":"9.4","DXVK":"2.4-async"} },
 *     { "kind": "FILES", "text": "Copy your game", "path": "/storage/emulated/0/GameNative/" },
 *     { "kind": "GET_DRIVER", "text": "Apply Turnip", "driverId": "turnip-24.3.0" },
 *     { "kind": "BIOS", "text": "No BIOS needed for PC games" },
 *     { "kind": "ACTION", "text": "Launch from the container list" },
 *     { "kind": "TIP", "text": "First boot compiles shaders — wait a minute" }
 *   ],
 *   "troubleshooting": [ { "symptom": "Black screen", "fix": "Switch DXVK to 1.x" } ]
 * }
 * ```
 */
@Serializable
data class GuideDto(
    val id: String,
    val gameId: String? = null,
    val emulatorId: String,
    val tier: Int = 4,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
    val dataAsOf: String? = null,
    val steps: List<GuideStepDto> = emptyList(),
    val troubleshooting: List<TroubleshootingDto> = emptyList(),
    /**
     * Chunk 1: optional raw importable GameNative per-game config (the
     * `<Game>_config.json`). Stored opaquely as an arbitrary flat JSON object;
     * we never model its fields. Null/missing for guides that don't carry one.
     */
    val gameNativeConfig: JsonElement? = null,
)

/** A single typed step in a guide. `kind` drives which UI affordance renders. */
@Serializable
data class GuideStepDto(
    /** GET_APP | GET_DRIVER | CONTAINER | FILES | BIOS | ACTION | TIP */
    val kind: String,
    val text: String,
    /** GET_APP: download URL. */
    val url: String? = null,
    /** GET_DRIVER: driver id to reuse the v0.6 apply flow. */
    val driverId: String? = null,
    /** FILES: a copy-able destination path. */
    val path: String? = null,
    /** CONTAINER: ordered key→value settings shown as a table. */
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class TroubleshootingDto(
    val symptom: String,
    val fix: String,
)

/* ---------- DTO → entity mapping ---------- */

internal val compatJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun CompatDbDto.toSnapshot(): CompatSnapshot = CompatSnapshot(
    games = games.map {
        GameEntity(
            id = it.id, title = it.title, titleSlug = it.titleSlug,
            platform = it.platform, releaseYear = it.releaseYear,
            region = it.region, coverUrl = it.coverUrl,
            exeHints = it.exeHints.takeIf { l -> l.isNotEmpty() }?.joinToString("|") { s -> s.lowercase() },
            romHints = it.romHints.takeIf { l -> l.isNotEmpty() }?.joinToString("|") { s -> s.lowercase() },
            alsoOn = it.alsoOn.takeIf { l -> l.isNotEmpty() }?.joinToString("|"),
            description = it.description,
            genres = it.genres.takeIf { l -> l.isNotEmpty() }?.joinToString("|"),
            setupSteps = it.setupSteps.takeIf { l -> l.isNotEmpty() }?.joinToString("\n"),
            knownIssues = it.knownIssues.takeIf { l -> l.isNotEmpty() }?.joinToString("\n"),
            recommendedRamGb = it.recommendedRamGb,
            bestVersionGuidance = it.bestVersionGuidance,
            screenshotUrls = it.screenshotUrls.takeIf { l -> l.isNotEmpty() }?.joinToString("|"),
            biosRequirements = it.biosRequirements.takeIf { l -> l.isNotEmpty() }?.joinToString("\n"),
            modsAndPatches = it.modsAndPatches.takeIf { l -> l.isNotEmpty() }?.joinToString("\n"),
            steamAppId = it.steamAppId,
            perEmulatorSetup = it.perEmulatorSetup
                .takeIf { m -> m.isNotEmpty() }
                ?.let { m ->
                    // Encode as a JSON object string. Use the shared compatJson
                    // so we stay consistent with the rest of the file.
                    val jsonObj = kotlinx.serialization.json.buildJsonObject {
                        m.forEach { (emulatorId, steps) ->
                            put(emulatorId, kotlinx.serialization.json.JsonPrimitive(steps.joinToString("\n")))
                        }
                    }
                    compatJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), jsonObj)
                },
        )
    },
    emulators = emulators.map {
        EmulatorEntity(
            id = it.id, name = it.name, packageId = it.packageId,
            platformTargets = it.platformTargets.joinToString("|"),
            sourceUrl = it.sourceUrl, notes = it.notes,
        )
    },
    presets = presets.map {
        PresetEntity(
            id = it.id, emulatorId = it.emulatorId, name = it.name,
            settingsJson = it.settings.toString(),
            driverId = it.driverId, notes = it.notes,
            // null in JSON → fall back to seed build time. The UI will still
            // show a "may be outdated" chip once the build date itself ages.
            dataAsOf = parseIsoDateOrZero(it.dataAsOf)
                .takeIf { ts -> ts > 0 } ?: io.github.mayusi.isitcompatible.BuildConfig.SEED_BUILT_AT_MS,
        )
    },
    reports = reports.map {
        ReportEntity(
            id = it.id, gameId = it.gameId, emulatorId = it.emulatorId,
            presetId = it.presetId,
            socFamily = it.device.socFamily,
            gpuVendor = it.device.gpuVendor,
            gpuModel = it.device.gpuModel,
            ramMb = it.device.ramMb,
            androidApi = it.device.androidApi,
            avgFps = it.avgFps, stability = it.stability,
            notes = it.notes, source = it.source, sourceRef = it.sourceRef,
            submittedAt = it.submittedAt,
        )
    },
    drivers = drivers.map {
        DriverEntity(
            id = it.id, name = it.name,
            gpuTargets = it.gpuTargets.joinToString("|"),
            downloadUrl = it.downloadUrl, sha256 = it.sha256,
            sizeBytes = it.sizeBytes, installedLocally = false,
            dataAsOf = parseIsoDateOrZero(it.dataAsOf)
                .takeIf { ts -> ts > 0 } ?: io.github.mayusi.isitcompatible.BuildConfig.SEED_BUILT_AT_MS,
            upstreamLatestTag = it.upstreamLatestTag,
        )
    },
    // v0.8: tier-0 is the "UNVETTED — do not ship" marker the offline research
    // tool stamps on un-reviewed drafts. Filter it out here as a hard backstop
    // so a stray draft can never surface to a user even if it lands in assets.
    guides = guides.filter { it.tier >= 1 }.map {
        GuideEntity(
            id = it.id,
            gameId = it.gameId,
            emulatorId = it.emulatorId,
            tier = it.tier,
            sourceLabel = it.sourceLabel,
            sourceUrl = it.sourceUrl,
            dataAsOf = parseIsoDateOrZero(it.dataAsOf)
                .takeIf { ts -> ts > 0 } ?: io.github.mayusi.isitcompatible.BuildConfig.SEED_BUILT_AT_MS,
            // Re-serialize the structured DTO lists into the JSON strings Room stores.
            stepsJson = compatJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(GuideStepDto.serializer()),
                it.steps,
            ),
            troubleshootingJson = it.troubleshooting
                .takeIf { t -> t.isNotEmpty() }
                ?.let { t ->
                    compatJson.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(TroubleshootingDto.serializer()),
                        t,
                    )
                },
            // Chunk 1: serialize the opaque config JsonElement to a compact
            // string; null stays null so guides without a config still load.
            gameNativeConfigJson = it.gameNativeConfig
                ?.let { cfg -> compatJson.encodeToString(JsonElement.serializer(), cfg) },
        )
    },
)

/**
 * v0.6: lenient ISO date → epoch ms. Accepts "2026-04-15" or null.
 * Anything we can't parse becomes 0, which the UI treats as "no date known
 * — fall back to bundled snapshot date."
 */
private fun parseIsoDateOrZero(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return runCatching {
        java.time.LocalDate.parse(iso)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}
