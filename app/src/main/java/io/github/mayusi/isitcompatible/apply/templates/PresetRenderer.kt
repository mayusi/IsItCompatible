package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One rendered config file ready to write to the staging dir. */
data class RenderedConfig(
    val fileName: String,
    val content: String,
    val description: String,   // human-readable note shown in INSTRUCTIONS.md
)

/**
 * Per-emulator template renderer. Each implementation knows the format of its
 * target emulator's config file and writes a settings file the user can import.
 *
 * All renderers are pure and return strings — the [io.github.mayusi.isitcompatible.apply.PresetStager]
 * decides where to write them.
 */
interface PresetRenderer {
    /** Emulator IDs this renderer handles. */
    val emulatorIds: Set<String>

    /**
     * @param gameNativeConfigJson Chunk 2: the resolved guide's verified, raw
     *        importable GameNative config (real flat schema), or null. Only the
     *        GameNative renderer consumes it; other renderers ignore it.
     */
    fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String? = null,
    ): RenderedConfig
}

private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

/** Reads [PresetEntity.settingsJson] into a JsonObject; empty on failure. */
internal fun PresetEntity.settingsObject(): JsonObject =
    runCatching { Json.parseToJsonElement(settingsJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

/** Convenience accessor for a string setting. */
internal fun JsonObject.s(key: String, default: String = ""): String =
    this[key]?.jsonPrimitive?.content ?: default

internal fun JsonObject.b(key: String, default: Boolean = false): Boolean =
    runCatching { this[key]?.jsonPrimitive?.content?.lowercase() in listOf("true", "on", "1", "yes") }
        .getOrElse { default }

internal object Pretty { val json: Json = prettyJson }
