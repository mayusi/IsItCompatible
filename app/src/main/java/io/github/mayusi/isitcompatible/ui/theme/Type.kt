package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Handheld Forge" typography system.
 *
 * Hierarchy philosophy:
 *  - display* / headline* / title* → ChakraPetch. Angular, mechanical, HUD-native.
 *    Hero numbers (displayLarge/displayMedium — FPS on verdict card) use Bold
 *    because Chakra Petch Bold has near-tabular digits that align perfectly.
 *  - body* → FontFamily.Default (system sans). Long descriptive text stays readable
 *    and doesn't fatigue the eye with geometric stroke shapes.
 *  - label* → ChakraPetch Medium. Chips, badges, button text, unit labels (fps, MB)
 *    all feel HUD-like with the spaced-caps letter-spacing bump.
 *
 * Sizes and line-heights are IDENTICAL to the previous defaults — no content
 * reflow. Only the font family and letter-spacing on labels changed.
 */
internal val AppTypography = Typography(

    // ── Display — hero numbers, verdict scores ────────────────────────────────
    displayLarge  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 57.sp,
        lineHeight   = 64.sp,
        letterSpacing = (-0.25).sp,   // tight at huge size looks intentional
    ),
    displayMedium = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        lineHeight   = 52.sp,
    ),
    displaySmall  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 36.sp,
        lineHeight   = 44.sp,
    ),

    // ── Headlines — section titles, screen headings ──────────────────────────
    headlineLarge  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
    ),
    headlineSmall  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Bold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
    ),

    // ── Titles — card titles, list-item primary text, toolbar titles ─────────
    titleLarge  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall  = TextStyle(
        fontFamily   = ChakraPetch,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── Body — long-form text, descriptions, notes ───────────────────────────
    // Keep system sans here: Chakra Petch is too mechanical for multi-line prose.
    bodyLarge  = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
    ),
    bodySmall  = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
    ),

    // ── Labels — chips, badges, buttons, unit text (fps / MB / ms) ──────────
    // ChakraPetch + wider spacing = instant HUD-chrome feel.
    labelLarge  = TextStyle(
        fontFamily    = ChakraPetch,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily    = ChakraPetch,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.75.sp,
    ),
    labelSmall  = TextStyle(
        fontFamily    = ChakraPetch,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 1.0.sp,
    ),
)
