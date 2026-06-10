package io.github.mayusi.isitcompatible.apply.templates

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GameNative — emits a REAL, importable GameNative `<Game>_config.json`.
 *
 * GameNative's "Import Config" reads a FLAT JSON object (the schema verified
 * against an Odin-exported config: id, name, screenSize, envVars, cpuList,
 * graphicsDriver, graphicsDriverConfig (a comma-separated key=value STRING),
 * dxwrapper, dxwrapperConfig (also a CSV key=value STRING), audioDriver,
 * wincomponents, drives, emulator, wineVersion, containerVariant, wow64Mode,
 * extraData{...}, ...). We produce exactly that, not a hand-typed text sheet.
 *
 * Two paths:
 *  - PREFERRED: if the resolved guide carried a verified [gameNativeConfigJson],
 *    we emit it VERBATIM — it's already the real schema.
 *  - FALLBACK: when no verified config exists, we synthesize a real-schema
 *    config from the preset + current sane defaults. This is valid GameNative
 *    JSON but is NOT a verified config; Chunk 3 gates whether it is even
 *    offered. Nothing here labels the fallback "verified".
 *
 * GameHub / Mobox are NOT GameNative and do not share this import schema, so
 * they keep the plain-text settings-sheet behavior (see [renderTextSheet]).
 */
class GameNativeTemplate : PresetRenderer {
    override val emulatorIds = setOf("gamenative", "gamehub", "mobox")

    override fun render(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        gameNativeConfigJson: String?,
    ): RenderedConfig {
        // Only GameNative imports this flat JSON schema. GameHub/Mobox don't, so
        // they keep the by-hand settings sheet.
        if (emulator.id != "gamenative") {
            return renderTextSheet(game, emulator, preset)
        }

        val configFileName = "${sanitizeTitle(game.title)}_config.json"

        // PREFERRED PATH: a verified config from the resolved guide, emitted as-is.
        if (!gameNativeConfigJson.isNullOrBlank()) {
            return RenderedConfig(
                fileName = configFileName,
                content = gameNativeConfigJson,
                description = "Open GameNative → Import Config → pick this ${configFileName} file.",
            )
        }

        // FALLBACK PATH: build a real-schema config from preset + current defaults.
        // (Unverified — Chunk 3 decides whether this is even offered.)
        return RenderedConfig(
            fileName = configFileName,
            content = buildFallbackConfig(game, preset),
            description = "Open GameNative → Import Config → pick this ${configFileName} file.",
        )
    }

    /** Synthesizes a valid real-schema GameNative config from the preset. */
    private fun buildFallbackConfig(game: GameEntity, preset: PresetEntity): String {
        val s = preset.settingsObject()

        // graphicsDriverConfig CSV — current Turnip/Vulkan defaults.
        val graphicsDriverConfig = csv(
            "vulkanVersion" to "1.3",
            "version" to "System",
            "blacklistedExtensions" to "",
            "maxDeviceMemory" to "0",
            "presentMode" to "mailbox",
            "adrenotoolsTurnip" to "1",
            "exposedDeviceExtensions" to "all",
        )

        // dxwrapperConfig CSV — current DXVK defaults.
        val dxwrapperConfig = csv(
            "version" to s.s("dxvkVersion", "async-1.10.3"),
            "framerate" to "0",
            "maxDeviceMemory" to "0",
            "async" to "1",
            "vkd3dVersion" to s.s("vkd3dVersion", "2.14.1"),
            "vkd3dFeatureLevel" to "12_1",
            "renderer" to "gl",
        )

        val config: JsonObject = buildJsonObject {
            put("id", JsonPrimitive(game.id))
            put("name", JsonPrimitive(game.title))
            put("screenSize", JsonPrimitive(s.s("screenSize", "1280x720")))
            put("envVars", JsonPrimitive(s.s("envVars", DEFAULT_ENV_VARS)))
            put("cpuList", JsonPrimitive(s.s("cpuList", "0,1,2,3,4,5,6,7")))
            put("cpuListWoW64", JsonPrimitive(s.s("cpuListWoW64", "4,5,6,7")))
            put("graphicsDriver", JsonPrimitive("wrapper"))
            put("graphicsDriverVersion", JsonPrimitive(""))
            put("graphicsDriverConfig", JsonPrimitive(graphicsDriverConfig))
            put("dxwrapper", JsonPrimitive("dxvk"))
            put("dxwrapperConfig", JsonPrimitive(dxwrapperConfig))
            put("audioDriver", JsonPrimitive(s.s("audioDriver", "pulseaudio")))
            put("wincomponents", JsonPrimitive(s.s("wincomponents", DEFAULT_WINCOMPONENTS)))
            put("drives", JsonPrimitive(s.s("drives", "")))
            put("box64Preset", JsonPrimitive(s.s("box64Preset", "COMPATIBILITY")))
            put("box64Version", JsonPrimitive(s.s("box64Version", "0.3.7")))
            put("fexcorePreset", JsonPrimitive(s.s("fexcorePreset", "INTERMEDIATE")))
            put("emulator", JsonPrimitive("FEXCore"))
            put("wineVersion", JsonPrimitive("proton-9.0-arm64ec"))
            put("containerVariant", JsonPrimitive("bionic"))
            put("wow64Mode", JsonPrimitive(true))
            put("extraData", buildJsonObject {
                put("appliedContainerVariant", JsonPrimitive("bionic"))
                put("appliedWineVersion", JsonPrimitive("proton-9.0-arm64ec"))
                put("config_changed", JsonPrimitive("true"))
            })
        }
        return Pretty.json.encodeToString(JsonObject.serializer(), config)
    }

    /**
     * GameHub / Mobox plain-text settings sheet. These launchers don't import a
     * config file; the user enters these values by hand in the app's own UI.
     */
    private fun renderTextSheet(
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
    ): RenderedConfig {
        val s = preset.settingsObject()
        val known = linkedMapOf(
            "protonVersion" to "Proton version",
            "wineVersion" to "Wine version",
            "dxvkVersion" to "DXVK version",
            "vkd3dVersion" to "VKD3D version",
            "box64Dynarec" to "Box64 dynarec",
            "box64Preset" to "Box64 preset",
            "dxvk" to "DXVK",
            "vulkanDriver" to "Graphics driver",
            "videoMemorySizeMB" to "Video memory (MB)",
            "containerName" to "Container name",
            "windowsVersion" to "Windows version",
            "args" to "Launch arguments",
            "envVars" to "Environment variables",
        )

        val lines = buildString {
            appendLine("${emulator.name.uppercase()} SETTINGS")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("Game:     ${game.title}")
            appendLine("Emulator: ${emulator.name}")
            appendLine("Preset:   ${preset.name}")
            appendLine()
            appendLine("This launcher doesn't import a config file — you enter")
            appendLine("these values by hand when you create the container.")
            appendLine()
            appendLine("SETTINGS TO ENTER")
            appendLine("-----------------")

            val seen = mutableSetOf<String>()
            for ((key, label) in known) {
                val v = s.rawValue(key) ?: continue
                appendLine("- $label: $v")
                seen += key
            }
            for ((key, value) in s) {
                if (key in seen) continue
                appendLine("- $key: ${value.toReadable()}")
            }

            preset.notes?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("NOTES")
                appendLine("-----")
                appendLine(it)
            }
        }

        return RenderedConfig(
            fileName = "${game.titleSlug}-${emulator.id}-settings.txt",
            content = lines,
            description = "Open ${emulator.name}, create/edit the container, and enter the values from this settings sheet by hand.",
        )
    }

    private companion object {
        const val DEFAULT_ENV_VARS =
            "ZINK_DESCRIPTORS=lazy MESA_SHADER_CACHE_DISABLE=false WINEESYNC=1 MESA_VK_WSI_PRESENT_MODE=mailbox"
        const val DEFAULT_WINCOMPONENTS =
            "direct3d=1,directsound=1,directmusic=0,directshow=0,directplay=0,vcrun2010=1,wmdecoder=1,opengl=0"
    }
}

/**
 * Joins key=value pairs into a comma-separated string with NO spaces, matching
 * GameNative's embedded sub-config format exactly (e.g. "a=1,b=2,c=3").
 */
internal fun csv(vararg pairs: Pair<String, String>): String =
    pairs.joinToString(",") { (k, v) -> "$k=$v" }

/** Filesystem-safe game title for the `<Title>_config.json` file name. */
private fun sanitizeTitle(title: String): String =
    title.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "")
        .replace(Regex("\\s+"), " ")
        .ifBlank { "GameNative" }

/** Raw string value for a key, or null if absent. */
private fun JsonObject.rawValue(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() ?: it.toString() }

private fun kotlinx.serialization.json.JsonElement.toReadable(): String =
    runCatching { jsonPrimitive.content }.getOrNull() ?: toString()
