package io.github.mayusi.isitcompatible.hardware

/**
 * Maps Build.SOC_MODEL / Build.HARDWARE strings to user-friendly SoC family names.
 *
 * Build.SOC_MODEL only exists on API 31+ and is wildly inconsistent across OEMs
 * (some report "SM8750", others "Snapdragon", some leave it blank). We normalise
 * to a stable family name the recommender can bucket on.
 *
 * This list is intentionally short — only chips actually shipping in modern
 * Android handhelds. Expand as new devices land.
 */
internal object SocCatalog {

    /**
     * @param socModel Build.SOC_MODEL (API 31+) — may be null/blank/junk.
     * @param hardware Build.HARDWARE — usually a kernel codename like "qcom",
     *                 sometimes more specific like "mt6989" on MediaTek.
     * @return Pair(family, model) — family is the user-facing label, model is the
     *         canonical chip identifier when we can resolve it.
     */
    fun resolve(socModel: String?, hardware: String?): Pair<String, String> {
        val sm = socModel?.takeIf { it.isNotBlank() }?.trim()
        val hw = hardware?.takeIf { it.isNotBlank() }?.trim() ?: ""

        // Try exact SM model first
        sm?.let { canonical ->
            ENTRIES[canonical.uppercase()]?.let { family ->
                return family to canonical
            }
        }
        // Fall back to Build.HARDWARE for older firmware
        ENTRIES[hw.uppercase()]?.let { family ->
            return family to hw
        }
        // Last resort: best-effort hint
        val combined = "$sm $hw".uppercase()
        for ((key, family) in HINTS) {
            if (key in combined) return family to (sm ?: hw)
        }
        return ("Unknown SoC" to (sm ?: hw))
    }

    private val ENTRIES: Map<String, String> = mapOf(
        // Snapdragon — modern flagship. AYN reports SOC_MODEL with a consumer codename
        // (CQ8725S on Odin 3 = SM8750 = 8 Elite; QCS8550 on Thor = SM8550 = 8 Gen 2).
        // Adreno pairings (for validation): 8 Elite=830, 8 Gen 3=750, 8 Gen 2=740,
        // 8+ Gen 1=730, 8 Gen 1=730, 888=660, 865=650.
        "CQ8725S" to "Snapdragon 8 Elite",
        "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "QCS8550" to "Snapdragon 8 Gen 2",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865",
        "SM7475" to "Snapdragon 7+ Gen 2",
        "SM7435" to "Snapdragon 7s Gen 2",
        "SM7325" to "Snapdragon 778G",
        "SM7150" to "Snapdragon 720G",      // Logitech G Cloud
        "SM6450" to "Snapdragon 6 Gen 1",
        "SM4350" to "Snapdragon 480",
        // Snapdragon gaming-handheld-specific G-series (AYANEO, Razer, Retroid)
        "SM7350" to "Snapdragon G3 Gen 3",  // AYANEO Pocket S2 (rebranded)
        "SM7325-AC" to "Snapdragon G3X Gen 2", // AYANEO Pocket S / DS / DMG
        "SM7250-AB" to "Snapdragon G3X Gen 1", // Razer Edge
        "SM6225" to "Snapdragon G1 Gen 2",   // Retroid Pocket Classic (approx)
        // MediaTek — Dimensity flagship
        "MT6989" to "Dimensity 9300",
        "MT6985" to "Dimensity 9200",
        "MT6983" to "Dimensity 9000",
        "MT6896" to "Dimensity 8200",
        "MT6893" to "Dimensity 1200",       // GPD XP Plus, AYANEO Pocket Air
        "MT6891Z" to "Dimensity 1100",      // Retroid Pocket 4 Pro
        "MT6877" to "Dimensity 900",        // Retroid Pocket 4
        // MediaTek — Helio (older handhelds)
        "MT6789" to "Helio G99",            // AYANEO Pocket Micro
        "MT6785T" to "Helio G90T",          // AYANEO Pocket Air Mini
        "MT6785" to "Helio G95",            // GPD XD Plus
        // MediaTek — Kompanio (Chromebook-grade)
        "MT8195" to "Kompanio 1380",
        "MT8186" to "Kompanio 520/528",
        // Unisoc — Anbernic / Retroid mid-tier
        "T826" to "Unisoc T826",            // Anbernic RG557
        "T820" to "Unisoc T820",            // Anbernic RG556
        "T618" to "Unisoc T618",            // Anbernic RG405M/V, Retroid Flip 2
        // Samsung Exynos
        "EXYNOS2400" to "Exynos 2400",
        "EXYNOS2200" to "Exynos 2200",
        // Rockchip (Anbernic budget, generic SBC handhelds)
        "RK3588" to "Rockchip RK3588",
        "RK3566" to "Rockchip RK3566",
        "RK3328" to "Rockchip RK3328",
        "RK3326" to "Rockchip RK3326",
        // Allwinner (Miyoo, TrimUI)
        "A523" to "Allwinner A523",         // TrimUI Smart Pro S
        "A133" to "Allwinner A133 Plus",    // TrimUI Brick / Smart Pro
        "A33" to "Allwinner A33",           // Miyoo A30
    )

    // Substring hints when neither key matched cleanly.
    private val HINTS: List<Pair<String, String>> = listOf(
        "ELITE" to "Snapdragon 8 Elite",
        "GEN 3" to "Snapdragon 8 Gen 3",
        "GEN 2" to "Snapdragon 8 Gen 2",
        "GEN 1" to "Snapdragon 8 Gen 1",
        "G3 GEN 3" to "Snapdragon G3 Gen 3",
        "G3X GEN 2" to "Snapdragon G3X Gen 2",
        "G3X GEN 1" to "Snapdragon G3X Gen 1",
        "DIMENSITY 9300" to "Dimensity 9300",
        "DIMENSITY 9200" to "Dimensity 9200",
        "DIMENSITY 8200" to "Dimensity 8200",
        "DIMENSITY 1200" to "Dimensity 1200",
        "DIMENSITY 1100" to "Dimensity 1100",
        "DIMENSITY 900" to "Dimensity 900",
        "HELIO G99" to "Helio G99",
        "HELIO G95" to "Helio G95",
        "HELIO G90" to "Helio G90T",
        "RK3588" to "Rockchip RK3588",
        "RK3566" to "Rockchip RK3566",
        "EXYNOS" to "Exynos (unspecified)",
        "UNISOC" to "Unisoc (unspecified)",
        "ALLWINNER" to "Allwinner (unspecified)",
    )
}
