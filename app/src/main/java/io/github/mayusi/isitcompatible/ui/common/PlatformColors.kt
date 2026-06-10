package io.github.mayusi.isitcompatible.ui.common

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for platform branding colors. Used by both Browse
 * rows and the Game Detail hero band so a Switch game feels the same across
 * the app.
 *
 * Picked to be vivid but not eye-searing on the dark background. Not the
 * official console brand colors (those would clash with each other) — these
 * are app-internal accents tuned to a single palette.
 */
object PlatformColors {

    fun primary(platform: String): Color = when (platform.uppercase()) {
        "WINDOWS" -> Color(0xFF2196F3)        // blue
        "PS1", "PS2", "PS3", "PSP", "PSVITA", "PS4", "PS5" -> Color(0xFF7E57C2) // purple
        "SWITCH" -> Color(0xFFE53935)         // red
        "WIIU", "WII", "GC" -> Color(0xFFFF7043)   // orange
        "N64" -> Color(0xFFFFB300)            // amber
        "N3DS", "NDS" -> Color(0xFF26A69A)    // teal
        "SNES", "NES", "GB", "GBC", "GBA" -> Color(0xFF9C27B0) // deeper purple
        "GENESIS", "MASTERSYSTEM", "GAMEGEAR" -> Color(0xFF00BCD4) // cyan
        "DC", "SATURN", "NAOMI" -> Color(0xFF00897B) // teal-dark
        else -> Color(0xFF9E9E9E)             // grey
    }

    /** A soft tint of the primary color suitable for card backgrounds. */
    fun tint(platform: String): Color = primary(platform).copy(alpha = 0.18f)

    /** A even softer wash for subtle header bands. */
    fun wash(platform: String): Color = primary(platform).copy(alpha = 0.10f)

    /** Stability → fps-number color. Mirrored here so all UIs share. */
    fun stability(s: String?): Color = when (s?.uppercase()) {
        "PERFECT" -> Color(0xFF4CAF50)
        "PLAYABLE" -> Color(0xFFAED581)
        "GLITCHY" -> Color(0xFFFFC107)
        "CRASHES" -> Color(0xFFEF5350)
        else -> Color(0xFF9E9E9E)
    }

    /** Friendly human label for stability values. */
    fun stabilityLabel(s: String?): String = when (s?.uppercase()) {
        "PERFECT" -> "Perfect"
        "PLAYABLE" -> "Playable"
        "GLITCHY" -> "Glitchy"
        "CRASHES" -> "Crashes"
        else -> s ?: "Unknown"
    }
}
