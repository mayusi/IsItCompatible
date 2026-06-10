package io.github.mayusi.isitcompatible.apply.templates

import com.google.common.truth.Truth.assertThat
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class RendererTest {

    private val ps2Game = GameEntity(
        id = "ps2:god-of-war", title = "God of War", titleSlug = "god-of-war",
        platform = "PS2", releaseYear = 2005, region = null, coverUrl = null,
        exeHints = null, romHints = null,
    )
    private val winGame = GameEntity(
        id = "win:skyrim-se", title = "Skyrim SE", titleSlug = "skyrim-se",
        platform = "WINDOWS", releaseYear = 2016, region = null, coverUrl = null,
        exeHints = null, romHints = null,
    )

    @Test
    fun `NetherSX2 renderer emits Vulkan + balanced preset`() {
        val out = NetherSx2Template().render(
            game = ps2Game,
            emulator = EmulatorEntity(
                id = "nethersx2-patch", name = "NetherSX2-Patch", packageId = null,
                platformTargets = "PS2", sourceUrl = null, notes = null,
            ),
            preset = PresetEntity(
                id = "p", emulatorId = "nethersx2-patch", name = "Balanced",
                settingsJson = """{"renderer":"Vulkan","internalRes":"2x","preset":"Balanced"}""",
                driverId = null, notes = null,
            ),
        )
        assertThat(out.fileName).isEqualTo("god-of-war-nethersx2-patch.ini")
        assertThat(out.content).contains("Renderer=14")        // Vulkan code
        assertThat(out.content).contains("upscale_multiplier=2")
        assertThat(out.content).contains("SpeedHacks_Preset=2") // Balanced code
    }

    @Test
    fun `Eden renderer maps resolution_setup correctly`() {
        val out = EdenTemplate().render(
            game = ps2Game.copy(id = "switch:totk", platform = "SWITCH"),
            emulator = EmulatorEntity(
                id = "eden", name = "Eden", packageId = null,
                platformTargets = "SWITCH", sourceUrl = null, notes = null,
            ),
            preset = PresetEntity(
                id = "p", emulatorId = "eden", name = "Perf",
                settingsJson = """{"resolution":"1x","gpuAccuracy":"Normal","cpuAccuracy":"Auto"}""",
                driverId = null, notes = null,
            ),
        )
        assertThat(out.content).contains("backend=1")
        assertThat(out.content).contains("resolution_setup=2") // 1x => 2
        assertThat(out.content).contains("gpu_accuracy=0")     // Normal => 0
    }

    @Test
    fun `Winlator template writes JSON with key wine settings`() {
        val out = WinlatorTemplate().render(
            game = winGame,
            emulator = EmulatorEntity(
                id = "winlator-cmod", name = "Winlator Cmod", packageId = null,
                platformTargets = "WINDOWS", sourceUrl = null, notes = null,
            ),
            preset = PresetEntity(
                id = "p", emulatorId = "winlator-cmod", name = "Cmod DXVK",
                settingsJson = """{"wineVersion":"9.4","dxvkVersion":"2.4-async","box64Dynarec":"ON"}""",
                driverId = null, notes = null,
            ),
        )
        assertThat(out.fileName).endsWith(".json")
        assertThat(out.content).contains("\"wineVersion\": \"9.4\"")
        assertThat(out.content).contains("\"dxvkVersion\": \"2.4-async\"")
    }

    private val gameNativeEmu = EmulatorEntity(
        id = "gamenative", name = "GameNative", packageId = null,
        platformTargets = "WINDOWS", sourceUrl = null, notes = null,
    )

    @Test
    fun `GameNative fallback emits valid real-schema config JSON`() {
        val out = GameNativeTemplate().render(
            game = winGame,
            emulator = gameNativeEmu,
            preset = PresetEntity(
                id = "p", emulatorId = "gamenative", name = "Default",
                settingsJson = """{"dxvkVersion":"async-1.10.3"}""",
                driverId = null, notes = null,
            ),
            gameNativeConfigJson = null,
        )

        // File name + mime contract.
        assertThat(out.fileName).isEqualTo("Skyrim SE_config.json")

        // Parses as valid JSON.
        val obj = Json.parseToJsonElement(out.content).jsonObject

        // Real-schema fields present.
        assertThat(obj["emulator"]!!.jsonPrimitive.content).isEqualTo("FEXCore")
        assertThat(obj["wineVersion"]!!.jsonPrimitive.content).isEqualTo("proton-9.0-arm64ec")
        assertThat(obj["containerVariant"]!!.jsonPrimitive.content).isEqualTo("bionic")

        // graphicsDriverConfig is a CSV key=value STRING with the Turnip flag.
        val gdc = obj["graphicsDriverConfig"]!!.jsonPrimitive.content
        assertThat(gdc).contains("adrenotoolsTurnip=1")
        assertThat(gdc).contains(",")
        assertThat(gdc).doesNotContain(", ")  // no spaces after commas

        // dxwrapperConfig is a CSV key=value STRING.
        val dwc = obj["dxwrapperConfig"]!!.jsonPrimitive.content
        assertThat(dwc).contains("version=async-1.10.3")
        assertThat(dwc).contains(",")
        assertThat(dwc).doesNotContain(", ")
    }

    @Test
    fun `GameNative emits verified guide config verbatim`() {
        val verified = """{"id":"STEAM_1","name":"Verified","emulator":"FEXCore","custom":"keep-me"}"""
        val out = GameNativeTemplate().render(
            game = winGame,
            emulator = gameNativeEmu,
            preset = PresetEntity(
                id = "p", emulatorId = "gamenative", name = "Default",
                settingsJson = "{}", driverId = null, notes = null,
            ),
            gameNativeConfigJson = verified,
        )
        assertThat(out.fileName).isEqualTo("Skyrim SE_config.json")
        assertThat(out.content).isEqualTo(verified)  // verbatim, not re-serialized
    }

    @Test
    fun `GameHub keeps the by-hand settings sheet (not the JSON schema)`() {
        val out = GameNativeTemplate().render(
            game = winGame,
            emulator = EmulatorEntity(
                id = "gamehub", name = "GameHub", packageId = null,
                platformTargets = "WINDOWS", sourceUrl = null, notes = null,
            ),
            preset = PresetEntity(
                id = "p", emulatorId = "gamehub", name = "Default",
                settingsJson = """{"wineVersion":"9.0"}""",
                driverId = null, notes = null,
            ),
            gameNativeConfigJson = null,
        )
        assertThat(out.fileName).endsWith("-settings.txt")
        assertThat(out.content).contains("Wine version: 9.0")
    }

    @Test
    fun `csv helper joins pairs with no spaces`() {
        assertThat(csv("a" to "1", "b" to "2", "c" to "3")).isEqualTo("a=1,b=2,c=3")
    }
}
