package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fallback for any emulator without a bespoke renderer (NetherSX2-Patch,
 * NetherSX2-Classic, ARMSX2, Eden, Citron, Azahar, Citra MMJ, Cemu, etc.).
 *
 * Writes a generic settings.json with every key from the preset, plus
 * metadata. The INSTRUCTIONS.md tells the user which screen in the target
 * emulator each setting maps to.
 */
class GenericJsonTemplate : PresetRenderer {
    override val emulatorIds = emptySet<String>()  // matched by fallback path

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val out: JsonObject = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("emulator", JsonPrimitive(emulator.name))
            put("gameTitle", JsonPrimitive(game.title))
            put("presetName", JsonPrimitive(preset.name))
            put("settings", s)
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-${emulator.id}.json",
            content = Pretty.json.encodeToString(JsonObject.serializer(), out),
            description = "Open ${emulator.name} → Settings → match these values."
        )
    }
}
