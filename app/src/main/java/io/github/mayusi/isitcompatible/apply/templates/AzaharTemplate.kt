package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity

/**
 * Azahar / Citra MMJ — both derived from Citra. Per-game settings live in
 * `config-per-game/<gameid>.ini`. We can't write there directly; this output
 * is shown to the user as the values to set in the in-app per-game editor.
 */
class AzaharTemplate : PresetRenderer {
    override val emulatorIds = setOf("azahar", "citra-mmj")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val ini = buildString {
            appendLine("# ${emulator.name} per-game override")
            appendLine("# Game: ${game.title}")
            appendLine()
            appendLine("[Renderer]")
            appendLine("resolution_factor=${s.s("resolution", "2x").removeSuffix("x")}")
            appendLine("use_vulkan=true")
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-${emulator.id}.ini",
            content = ini,
            description = "Open ${emulator.name} → long-press the game → Properties → set values from this .ini."
        )
    }
}
