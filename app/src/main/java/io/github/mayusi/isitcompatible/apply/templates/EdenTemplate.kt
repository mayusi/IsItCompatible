package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity

/**
 * Eden / Citron / Yuzu-derivatives — all read a similar `config.ini` shape.
 *
 * The values here line up with Yuzu's settings keys; Eden and Citron both
 * preserve them for compatibility. User imports as a per-game override.
 */
class EdenTemplate : PresetRenderer {
    override val emulatorIds = setOf("eden", "citron")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val ini = buildString {
            appendLine("# ${emulator.name} per-game override")
            appendLine("# Game: ${game.title}  (${game.id})")
            appendLine("# Preset: ${preset.name}")
            appendLine()
            appendLine("[Renderer]")
            appendLine("backend=1                 # 0=OpenGL, 1=Vulkan")
            appendLine("gpu_accuracy=${gpuAccuracy(s.s("gpuAccuracy", "Normal"))}")
            appendLine("resolution_setup=${resCode(s.s("resolution", "1x"))}")
            appendLine()
            appendLine("[Cpu]")
            appendLine("cpu_accuracy=${cpuAccuracy(s.s("cpuAccuracy", "Auto"))}")
        }
        return RenderedConfig(
            fileName = "${game.titleSlug}-${emulator.id}.ini",
            content = ini,
            description = "Open ${emulator.name} → Properties → Add per-game override; copy values from this .ini."
        )
    }

    private fun gpuAccuracy(v: String): String = when (v.uppercase()) {
        "NORMAL" -> "0"
        "HIGH" -> "1"
        "EXTREME" -> "2"
        else -> "0"
    }

    private fun cpuAccuracy(v: String): String = when (v.uppercase()) {
        "AUTO" -> "0"
        "ACCURATE" -> "1"
        "UNSAFE" -> "2"
        else -> "0"
    }

    /** Yuzu's resolution_setup: 2=1x, 3=2x, 4=3x, 5=4x. */
    private fun resCode(v: String): String = when (v.lowercase()) {
        "0.5x" -> "1"; "1x" -> "2"; "2x" -> "3"; "3x" -> "4"; "4x" -> "5"
        else -> "2"
    }
}
