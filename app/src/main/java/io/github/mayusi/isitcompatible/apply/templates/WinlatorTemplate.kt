package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Winlator (vanilla + Cmod) container hint file.
 *
 * Winlator doesn't have a documented import-a-container intent on Android,
 * so we write a JSON descriptor next to the staging dir that summarises every
 * setting the user needs to toggle in Winlator's container editor. The
 * `INSTRUCTIONS.md` references this file by path.
 */
class WinlatorTemplate : PresetRenderer {
    override val emulatorIds = setOf("winlator", "winlator-cmod")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val container: JsonObject = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("emulator", JsonPrimitive(emulator.name))
            put("gameTitle", JsonPrimitive(game.title))
            put("presetName", JsonPrimitive(preset.name))
            put("wineVersion", JsonPrimitive(s.s("wineVersion", "9.0")))
            put("dxvkVersion", JsonPrimitive(s.s("dxvkVersion", "2.3.1")))
            put("vkd3dVersion", JsonPrimitive(s.s("vkd3dVersion", "2.12")))
            put("box64Dynarec", JsonPrimitive(s.s("box64Dynarec", "ON")))
            put("cpuPreset", JsonPrimitive(s.s("cpuPreset", "balanced")))
            put("containerProfile", JsonPrimitive(s.s("container", "default")))
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-winlator.json",
            content = Pretty.json.encodeToString(JsonObject.serializer(), container),
            description = "Open ${emulator.name} → Container Editor → match these values."
        )
    }
}
