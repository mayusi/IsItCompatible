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

    // ── existing behaviour ────────────────────────────────────────────────────

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

    // ── v1.1: recency weighting ───────────────────────────────────────────────

    @Test
    fun `recencyWeight returns full weight for fresh reports`() {
        val r = Recommender()
        val nowMs = System.currentTimeMillis()
        // 1 month ago
        val freshAt = nowMs - (30L * 24 * 60 * 60 * 1000)
        assertThat(r.recencyWeight(freshAt, nowMs)).isEqualTo(1.00)
    }

    @Test
    fun `recencyWeight returns aging weight for 12-month-old reports`() {
        val r = Recommender()
        val nowMs = System.currentTimeMillis()
        val agingAt = nowMs - (12L * 30 * 24 * 60 * 60 * 1000)
        assertThat(r.recencyWeight(agingAt, nowMs)).isEqualTo(0.85)
    }

    @Test
    fun `recencyWeight returns stale weight for 2-year-old reports`() {
        val r = Recommender()
        val nowMs = System.currentTimeMillis()
        val staleAt = nowMs - (24L * 30 * 24 * 60 * 60 * 1000)
        assertThat(r.recencyWeight(staleAt, nowMs)).isEqualTo(0.70)
    }

    @Test
    fun `recencyWeight treats submittedAt of 0 as stale`() {
        val r = Recommender()
        assertThat(r.recencyWeight(0L, System.currentTimeMillis())).isEqualTo(0.70)
    }

    @Test
    fun `a fresh perfect report beats a stale perfect report at equal fps`() {
        val nowMs = System.currentTimeMillis()
        val freshAt = nowMs - (2L * 30 * 24 * 60 * 60 * 1000)      // 2 months ago
        val staleAt = nowMs - (24L * 30 * 24 * 60 * 60 * 1000)     // 2 years ago

        val reports = listOf(
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384, submittedAt = freshAt),
            report("r2", emu = "B", preset = "p2", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384, submittedAt = staleAt),
        )
        val top = Recommender().rank(reports, sd8e, topK = 2, nowMs = nowMs)
        // Fresh report's emu should rank above the stale-only emu
        assertThat(top.first().emulatorId).isEqualTo("A")
    }

    @Test
    fun `same-device stale report still beats different-device fresh report`() {
        // Hardware-match (bucket) must dominate over recency — same-SoC+RAM must win.
        val nowMs = System.currentTimeMillis()
        val freshAt = nowMs - (1L * 30 * 24 * 60 * 60 * 1000)   // 1 month ago
        val staleAt = nowMs - (24L * 30 * 24 * 60 * 60 * 1000)  // 2 years ago

        val reports = listOf(
            // Same SoC+RAM, stale — should still win over the fresh wrong-device report.
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384, submittedAt = staleAt),
            // Different SoC, fresh — but wrong bucket.
            report("r2", emu = "B", preset = "p2", fps = 90, stab = "PERFECT",
                soc = "Exynos 2400", ram = 16384, gpuVendor = "MALI", submittedAt = freshAt),
        )
        val top = Recommender().rank(reports, sd8e, topK = 1, nowMs = nowMs)
        // Bucket cascade: r1 matches SAME_SOC_AND_RAM; r2 only matches ANY_DEVICE.
        // So only r1 is ever considered — bucket cascade already filters correctly.
        assertThat(top.first().emulatorId).isEqualTo("A")
        assertThat(top.first().bucket).isEqualTo(Bucket.SAME_SOC_AND_RAM)
    }

    // ── v1.1: conflict detection ──────────────────────────────────────────────

    @Test
    fun `single report never has conflict`() {
        val r = Recommender()
        val rs = listOf(report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
            soc = "Snapdragon 8 Elite", ram = 16384))
        val (conflict, note) = r.detectConflict(rs)
        assertThat(conflict).isFalse()
        assertThat(note).isNull()
    }

    @Test
    fun `agreeing reports have no conflict`() {
        val r = Recommender()
        val rs = listOf(
            report("r1", emu = "A", preset = "p1", fps = 58, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r3", emu = "A", preset = "p1", fps = 62, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val (conflict, _) = r.detectConflict(rs)
        assertThat(conflict).isFalse()
    }

    @Test
    fun `mixed PERFECT and CRASHES stability with no clear majority flags conflict`() {
        val r = Recommender()
        val rs = listOf(
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "A", preset = "p1", fps = 0, stab = "CRASHES",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r3", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r4", emu = "A", preset = "p1", fps = 0, stab = "CRASHES",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val (conflict, note) = r.detectConflict(rs)
        assertThat(conflict).isTrue()
        assertThat(note).isNotNull()
    }

    @Test
    fun `wide fps spread with enough reports flags conflict`() {
        val r = Recommender()
        // 3 reports (meets MIN_REPORTS_FOR_FPS_CONFLICT=3) with a 40fps spread (>=25)
        val rs = listOf(
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "A", preset = "p1", fps = 30, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r3", emu = "A", preset = "p1", fps = 20, stab = "PLAYABLE",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val (conflict, note) = r.detectConflict(rs)
        assertThat(conflict).isTrue()
        assertThat(note).contains("FPS")
    }

    @Test
    fun `fps spread below threshold is not a conflict`() {
        val r = Recommender()
        val rs = listOf(
            report("r1", emu = "A", preset = "p1", fps = 58, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r2", emu = "A", preset = "p1", fps = 62, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
            report("r3", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384),
        )
        val (conflict, _) = r.detectConflict(rs)
        assertThat(conflict).isFalse()
    }

    // ── v1.1: effective confidence ────────────────────────────────────────────

    @Test
    fun `single report lowers effective confidence by one tier`() {
        val r = Recommender()
        // STRONG base + 1 report → should become MODERATE
        val eff = r.computeEffectiveConfidence(
            base = Confidence.STRONG,
            reportCount = 1,
            hasConflict = false,
            allStale = false,
        )
        assertThat(eff).isEqualTo(Confidence.MODERATE)
    }

    @Test
    fun `conflict lowers effective confidence by one tier`() {
        val r = Recommender()
        val eff = r.computeEffectiveConfidence(
            base = Confidence.MODERATE,
            reportCount = 5,
            hasConflict = true,
            allStale = false,
        )
        assertThat(eff).isEqualTo(Confidence.WEAK)
    }

    @Test
    fun `single conflicted stale report degrades STRONG to VERY_WEAK`() {
        val r = Recommender()
        // STRONG + count penalty + conflict penalty + stale penalty = VERY_WEAK
        val eff = r.computeEffectiveConfidence(
            base = Confidence.STRONG,
            reportCount = 1,
            hasConflict = true,
            allStale = true,
        )
        assertThat(eff).isEqualTo(Confidence.VERY_WEAK)
    }

    @Test
    fun `many fresh agreeing reports keep STRONG confidence`() {
        val r = Recommender()
        val eff = r.computeEffectiveConfidence(
            base = Confidence.STRONG,
            reportCount = 10,
            hasConflict = false,
            allStale = false,
        )
        assertThat(eff).isEqualTo(Confidence.STRONG)
    }

    @Test
    fun `effective confidence never exceeds base bucket confidence`() {
        val r = Recommender()
        // WEAK base — even with perfect data, can't become STRONG.
        val eff = r.computeEffectiveConfidence(
            base = Confidence.WEAK,
            reportCount = 100,
            hasConflict = false,
            allStale = false,
        )
        assertThat(eff).isEqualTo(Confidence.WEAK)
    }

    @Test
    fun `effective confidence is wired into rank results`() {
        // A single conflicted report at SAME_SOC_AND_RAM should produce WEAK, not STRONG.
        val nowMs = System.currentTimeMillis()
        val staleAt = nowMs - (24L * 30 * 24 * 60 * 60 * 1000)
        val reports = listOf(
            // Two conflicting reports for same emu+preset
            report("r1", emu = "A", preset = "p1", fps = 60, stab = "PERFECT",
                soc = "Snapdragon 8 Elite", ram = 16384, submittedAt = staleAt),
            report("r2", emu = "A", preset = "p1", fps = 5, stab = "CRASHES",
                soc = "Snapdragon 8 Elite", ram = 16384, submittedAt = staleAt),
        )
        val top = Recommender().rank(reports, sd8e, topK = 1, nowMs = nowMs)
        assertThat(top).hasSize(1)
        // Bucket is STRONG, but effective confidence should be downgraded.
        assertThat(top.first().bucket.confidence).isEqualTo(Confidence.STRONG)
        assertThat(top.first().effectiveConfidence).isNotEqualTo(Confidence.STRONG)
        assertThat(top.first().hasHighConflict).isTrue()
    }

    private fun report(
        id: String, emu: String, preset: String?, fps: Int?, stab: String,
        soc: String, ram: Int, gpuVendor: String = "ADRENO",
        submittedAt: Long = 0L,
    ) = ReportEntity(
        id = id, gameId = "game", emulatorId = emu, presetId = preset,
        socFamily = soc, gpuVendor = gpuVendor, gpuModel = "Adreno 750", ramMb = ram, androidApi = 34,
        avgFps = fps, stability = stab, notes = null, source = "OUR_GITHUB", sourceRef = null,
        submittedAt = submittedAt,
    )
}
