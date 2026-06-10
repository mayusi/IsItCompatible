package io.github.mayusi.isitcompatible.apply

import io.github.mayusi.isitcompatible.apply.templates.RenderedConfig
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity

/**
 * Generates a PLAIN-TEXT instructions block the user follows to make the
 * recommended preset live in the target emulator.
 *
 * Written to `INSTRUCTIONS.txt` (not .md) next to the config file — Android
 * has no built-in Markdown viewer, so .txt is the only format every device
 * can open by tapping it. Kept clean ASCII so it reads fine in any text app.
 */
object InstructionsRenderer {

    fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        renderedConfig: RenderedConfig,
        driver: DriverEntity?,
        configPath: String,            // path of the written config inside the staging tree
        driverPath: String?,           // path of the written driver, if any
    ): String = buildString {
        appendLine("APPLY ${preset.name} FOR ${game.title}")
        appendLine("=".repeat(48))
        appendLine()
        appendLine("Target emulator: ${emulator.name}")
        emulator.packageId?.let { appendLine("Package: $it") }
        appendLine()
        appendLine("STEPS")
        appendLine("-----")

        var step = 1
        appendLine("$step. Open ${emulator.name} on your device.")
        step++

        if (driver != null && driverPath != null) {
            appendLine("$step. Install the GPU driver:")
            appendLine("     - Open the emulator's GPU driver settings.")
            appendLine("     - Tap \"Install custom driver\".")
            appendLine("     - Point it at: $driverPath")
            appendLine("     - File: ${driver.name}")
            step++
        }

        appendLine("$step. ${renderedConfig.description}")
        appendLine("     - Config file written to: $configPath")
        step++

        appendLine("$step. Launch ${game.title}.")
        appendLine()
        appendLine("WHAT TO EXPECT")
        appendLine("--------------")
        appendLine("- Preset: ${preset.name}")
        if (preset.notes?.isNotBlank() == true) appendLine("- Notes: ${preset.notes}")
        if (emulator.notes?.isNotBlank() == true) appendLine("- Emulator notes: ${emulator.notes}")
        appendLine()
        appendLine("If it works, please come back and submit a report via")
        appendLine("Settings -> Submit a report. That's how we improve")
        appendLine("recommendations for the next person.")
    }
}
