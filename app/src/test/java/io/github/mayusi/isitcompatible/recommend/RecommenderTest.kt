package io.github.mayusi.isitcompatible.recommend

import com.google.common.truth.Truth.assertThat
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.GpuVendor
import org.junit.Test

class RecommenderTest {

    private val sd8e = DeviceFingerprint(
        socFamily = "Snapdragon 8 Elite",
        socModel = "SM8750",
        gpuVendor = GpuVendor.ADRENO,
        gpuModel = "Adreno 750",
        gpuDriver = "",
        totalRamMb = 16384,
        androidApi = 34,
        androidRelease = "14",
        vulkanApiVersion = null,
        manufacturer = "AYN", model = "Odin 3",
    )

    @Test
    fun `picks higher fps + better stability when device matches`() {
        val reports = listOf(
            report("r1", emu = "winlator-cmod", preset = "cmod-1", fps = 50, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "winlator-cmod", preset = "cmod-1", fps = 52, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r3", emu = "gamenative", preset = "gn-1", fps = 40, stab = "GLITCHY",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val top = Recommender().rank(reports, sd8e, topK = 3)
        assertThat(top.first().emulatorId).isEqualTo("winlator-cmod")
        assertThat(top.first().bucket).isEqualTo(Bucket.SAME_SOC_AND_RAM)
    }

    @Test
    fun `widens to same-GPU-vendor bucket when no SoC match`() {
        val reports = listOf(
            report("r1", emu = "winlator", preset = "v-1", fps = 32, stab = "PLAYABLE",
                soc = "Snapdragon 8 Gen 2", ram = 12288),
            report("r2", emu = "winlator", preset = "v-1", fps = 35, stab = "PLAYABLE",
                soc = "Snapdragon 8 Gen 2", ram = 12288),
        )
        val top = Recommender().rank(reports, sd8e)
        assertThat(top).isNotEmpty()
        // Different SoC family but Adreno → matches SAME_SOC_AND_RAM tier? No — different family.
        // Should land in SAME_GPU_VENDOR.
        assertThat(top.first().bucket).isEqualTo(Bucket.SAME_GPU_VENDOR)
    }

    @Test
    fun `falls back to ANY_DEVICE when nothing else matches`() {
        val reports = listOf(
            report("r1", emu = "winlator", preset = "v-1", fps = 30, stab = "PLAYABLE",
                soc = "Exynos 2400", ram = 8192, gpuVendor = "MALI"),
        )
        val top = Recommender().rank(reports, sd8e)
        assertThat(top).hasSize(1)
        assertThat(top.first().bucket).isEqualTo(Bucket.ANY_DEVICE)
    }

    @Test
    fun `empty report list yields empty recommendations`() {
        assertThat(Recommender().rank(emptyList(), sd8e)).isEmpty()
    }

    @Test
    fun `picks PERFECT over PLAYABLE at equal fps`() {
        val reports = listOf(
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "B", preset = "p2", fps = 60, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val top = Recommender().rank(reports, sd8e, topK = 2)
        assertThat(top.first().emulatorId).isEqualTo("A")
    }

    private fun report(
        id: String, emu: String, preset: String?, fps: Int?, stab: String,
        soc: String, ram: Int, gpuVendor: String = "ADRENO",
    ) = ReportEntity(
        id = id, gameId = "game", emulatorId = emu, presetId = preset,
        socFamily = soc, gpuVendor = gpuVendor, gpuModel = "Adreno 750", ramMb = ram, androidApi = 34,
        avgFps = fps, stability = stab, notes = null, source = "OUR_GITHUB", sourceRef = null,
        submittedAt = 0L,
    )
}
