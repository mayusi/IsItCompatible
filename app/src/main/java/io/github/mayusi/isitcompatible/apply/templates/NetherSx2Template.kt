package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity

/**
 * NetherSX2-Patch / NetherSX2-Classic / ARMSX2 — all wrap the AetherSX2 codebase
 * so they all read PCSX2-style per-game `.ini` files.
 *
 * We can't write to other apps' private storage on Android without root, so this
 * file goes into the staging tree; the INSTRUCTIONS.md tells the user which
 * options to flip inside the NetherSX2 settings UI based on the keys here.
 */
class NetherSx2Template : PresetRenderer {
    override val emulatorIds = setOf("nethersx2-patch", "nethersx2-classic", "armsx2")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val ini = buildString {
            appendLine("# ${emulator.name} per-game settings")
            appendLine("# Game: ${game.title}  (${game.id})")
            appendLine("# Preset: ${preset.name}")
            appendLine()
            appendLine("[EmuCore/GS]")
            appendLine("Renderer=${rendererCode(s.s("renderer", "Vulkan"))}")
            appendLine("upscale_multiplier=${irFactor(s.s("internalRes", "2x"))}")
            appendLine()
            appendLine("[EmuCore]")
            appendLine("# Map the preset name onto NetherSX2's built-in presets:")
            appendLine("# Safest / Safe / Balanced / Aggressive / VeryAggressive")
            appendLine("SpeedHacks_Preset=${presetCode(s.s("preset", "Balanced"))}")
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-${emulator.id}.ini",
            content = ini,
            description = "Open ${emulator.name} → Settings → Graphics + Speedhacks; apply the values from this .ini."
        )
    }

    /** PCSX2 uses 0=DX11, 12=OpenGL, 14=Vulkan; sticking to common ones. */
    private fun rendererCode(v: String): String = when (v.uppercase()) {
        "VULKAN" -> "14"
        "OPENGL" -> "12"
        else -> "14"
    }

    private fun irFactor(v: String): String = v.removeSuffix("x").trim().ifEmpty { "2" }

    /** NetherSX2's SpeedHacks_Preset is an int 0–5. */
    private fun presetCode(v: String): String = when (v.uppercase()) {
        "SAFEST" -> "0"
        "SAFE" -> "1"
        "BALANCED" -> "2"
        "AGGRESSIVE" -> "3"
        "VERYAGGRESSIVE", "VERY AGGRESSIVE" -> "4"
        "PERFORMANCE" -> "3"
        else -> "2"
    }
}
