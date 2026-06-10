package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity

/**
 * Cemu (Wii U) — uses per-game graphic packs and a settings.xml entry per title.
 * We emit a hint file that lines up with the user-visible labels in Cemu's
 * graphic-pack manager.
 */
class CemuTemplate : PresetRenderer {
    override val emulatorIds = setOf("cemu")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val txt = buildString {
            appendLine("# Cemu per-game settings hint")
            appendLine("# Game: ${game.title}")
            appendLine("# Preset: ${preset.name}")
            appendLine()
            appendLine("Renderer: Vulkan")
            appendLine("Resolution: ${s.s("resolution", "1x")}")
            appendLine("VSync: ON")
            appendLine("Async shader compile: ON")
            appendLine()
            appendLine("Notes:")
            appendLine("- Install matching graphic pack from Cemu → Options → Graphic packs.")
            appendLine("- Make sure cemuhook / community patches are up to date for this title.")
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-cemu.txt",
            content = txt,
            description = "Open Cemu → Options → Game profile + Graphic packs; match the values here."
        )
    }
}
