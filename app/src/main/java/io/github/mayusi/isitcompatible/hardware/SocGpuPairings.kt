package io.github.mayusi.isitcompatible.hardware

/**
 * Source of truth for which GPU ships with which SoC family.
 *
 * Used by [validateReportDevice] to catch seed-data errors at load time
 * (e.g. someone claiming "Snapdragon 8 Elite + Adreno 750" when the Elite
 * actually ships with Adreno 830 — a real bug we hit in v0.2 seed data).
 *
 * Keep this current with new Snapdragon / Dimensity / Exynos / Rockchip
 * launches. Mali/PowerVR specifics aren't enforced (too many sub-variants).
 */
object SocGpuPairings {

    /** Returns the canonical GPU model that ships with [socFamily], or null if unknown. */
    fun expectedGpu(socFamily: String): String? =
        SOC_TO_GPU[socFamily.trim()]

    /**
     * @return null if the pairing is fine (or one side is unknown);
     *         otherwise a human-readable warning string.
     */
    fun validate(socFamily: String, gpuModel: String): String? {
        val expected = expectedGpu(socFamily) ?: return null
        val actual = gpuModel.trim()
        if (expected.equals(actual, ignoreCase = true)) return null
        return "$socFamily ships with $expected, not $actual"
    }

    private val SOC_TO_GPU: Map<String, String> = mapOf(
        // Snapdragon — modern handheld-grade
        "Snapdragon 8 Elite"  to "Adreno 830",
        "Snapdragon 8 Gen 3"  to "Adreno 750",
        "Snapdragon 8 Gen 2"  to "Adreno 740",
        "Snapdragon 8+ Gen 1" to "Adreno 730",
        "Snapdragon 8 Gen 1"  to "Adreno 730",
        "Snapdragon 888"      to "Adreno 660",
        "Snapdragon 865"      to "Adreno 650",
        "Snapdragon 7+ Gen 2" to "Adreno 725",
        "Snapdragon 7s Gen 2" to "Adreno 710",
        "Snapdragon 778G"     to "Adreno 642L",
        "Snapdragon 720G"     to "Adreno 618",
        "Snapdragon 6 Gen 1"  to "Adreno 710",
        "Snapdragon 480"      to "Adreno 619",
        // Snapdragon G-series (gaming SoCs in AYANEO Pocket, Razer Edge, Retroid)
        "Snapdragon G3 Gen 3" to "Adreno A32",
        "Snapdragon G3X Gen 2" to "Adreno A32",
        "Snapdragon G3X Gen 1" to "Adreno A32-1",
        "Snapdragon G1 Gen 2" to "Adreno 619",
        // MediaTek Dimensity flagship
        "Dimensity 9300"      to "Mali-G720 Immortalis",
        "Dimensity 9200"      to "Mali-G715 Immortalis",
        "Dimensity 9000"      to "Mali-G710",
        "Dimensity 8200"      to "Mali-G610",
        "Dimensity 1200"      to "Mali-G77 MC9",
        "Dimensity 1100"      to "Mali-G77 MC9",
        "Dimensity 900"       to "Mali-G68 MC4",
        // MediaTek Helio (older handhelds)
        "Helio G99"           to "Mali-G57 MC2",
        "Helio G95"           to "Mali-G76 MC4",
        "Helio G90T"          to "Mali-G76 MC4",
        // Unisoc (Anbernic / Retroid mid-tier)
        "Unisoc T826"         to "Mali-G57 MP4",
        "Unisoc T820"         to "Mali-G57 MP4",
        "Unisoc T618"         to "Mali-G52 MP2",
        // Rockchip handheld SBCs (Anbernic etc.)
        "Rockchip RK3588"     to "Mali-G610",
        "Rockchip RK3566"     to "Mali-G52",
        "Rockchip RK3328"     to "Mali-450",
        "Rockchip RK3326"     to "Mali-G31",
        // Allwinner retro handhelds (Miyoo, TrimUI)
        "Allwinner A523"      to "Mali-G57 MP2",
        "Allwinner A133 Plus" to "PowerVR GE8300",
        "Allwinner A33"       to "Mali-400 MP2",
        // Samsung Exynos
        "Exynos 2400"         to "Xclipse 940",
        "Exynos 2200"         to "Xclipse 920",
    )
}
