package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Handheld Forge" semantic color tokens — dark-mode tuned.
 *
 * PURPOSE
 * -------
 * The screens currently hardcode ~50 raw hex values, many of which are LIGHT
 * pastels (0xFFE3F2FD, 0xFFE8F5E9, 0xFFF3E5F5, 0xFFFFF8E1, etc.) that render
 * as near-white boxes on the dark #161C24 surface — breaking dark mode entirely.
 *
 * These tokens are the dark-correct replacements. All values are mid-bright
 * accents that sit well on surfaces in the range #0A0E14 → #1E2733.
 *
 * USAGE PATTERN (for screen-redesign agents)
 * -------------------------------------------
 * Tinted background (chip/badge bg):   color.copy(alpha = 0.18f)
 * Full-color text/icon on dark bg:     the color at full opacity (or .copy(alpha = 0.9f))
 * Border / outline accent:             color.copy(alpha = 0.35f)
 *
 * Example — report-source chip:
 *   val bg   = AppColors.sourceEmuReady.copy(alpha = 0.18f)
 *   val text = AppColors.sourceEmuReady
 *
 * STATUS COLORS
 * ─────────────
 * These mirror the theme's error color and extend it with success/warning/neutral.
 * They intentionally echo the PlatformColors.stability() function so both APIs
 * produce visually consistent outputs — prefer updating PlatformColors to use
 * these tokens in a future cleanup pass.
 */
object AppColors {

    // ── Status ────────────────────────────────────────────────────────────────

    /** Emerald green — "Perfect" / success state */
    val success = Color(0xFF34D399)

    /** Amber — "Glitchy" / warning state */
    val warning = Color(0xFFFBBF24)

    /** Coral red — "Crashes" / error state (matches DarkPalette.error = 0xFFFF6B6B) */
    val danger  = Color(0xFFFF6B6B)

    /** Blue-grey — unknown / neutral / "Playable" secondary */
    val neutral = Color(0xFF8F9EB0)   // matches onSurfaceVariant in DarkPalette

    // ── Report-source accents ─────────────────────────────────────────────────
    // Replace old light pastels: 0xFFE3F2FD (blue), 0xFFE8F5E9 (green),
    // 0xFFF3E5F5 (purple), 0xFFFFF8E1 (amber) — those are near-white on dark bg.

    /** EmuReady data source — sky blue */
    val sourceEmuReady  = Color(0xFF60A5FA)

    /** Community-submitted data — emerald green */
    val sourceCommunity = Color(0xFF34D399)

    /** Bundled / built-in data — soft purple */
    val sourceBundled   = Color(0xFFC084FC)

    /** Estimated / inferred data — amber */
    val sourceEstimated = Color(0xFFFBBF24)

    // ── SoC tier accents ──────────────────────────────────────────────────────
    // Replace AutoDetect screen's raw 0xFF6200EE / 0xFF2196F3 / etc. hardcodes.
    // Chosen to complement the cyan primary without duplicating it.

    /** Flagship tier (e.g. SD 8 Gen 3, Dimensity 9300) — vivid purple */
    val tierFlagship = Color(0xFFC084FC)

    /** High-end tier (e.g. SD 7s Gen 3, Dimensity 8300) — sky blue */
    val tierHighEnd  = Color(0xFF60A5FA)

    /** Mid-range tier (e.g. SD 6 Gen 1, Helio G99) — emerald */
    val tierMidRange = Color(0xFF34D399)

    /** Entry-level tier — blue-grey (neutral, de-emphasised) */
    val tierEntry    = Color(0xFF8F9EB0)

    // ── Misc accents ──────────────────────────────────────────────────────────

    /** Star / favorites — amber (replaces 0xFFFFC107 hardcodes throughout) */
    val favorite = Color(0xFFFBBF24)

    /** Cyan accent — matches primary in DarkPalette; for inline icon tinting */
    val primaryCyan = Color(0xFF6EE7F7)
}
