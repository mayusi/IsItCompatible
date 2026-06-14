package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.mayusi.isitcompatible.R

/**
 * HUD-style typefaces for the "Handheld Forge" design system.
 *
 * Chakra Petch — primary display + UI chrome font. Its angular geometry and
 * mechanical stroke endings give the app a premium firmware / handheld-device
 * aesthetic. Works especially well at large sizes (FPS numbers, verdict titles).
 *
 * Michroma — reserved for extreme hero moments (e.g. a single large stat number
 * that needs maximum impact). Fully monospaced-feeling, very techy. Use sparingly.
 */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular,  FontWeight.Normal),
    Font(R.font.chakra_petch_medium,   FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold,     FontWeight.Bold),
)

val Michroma = FontFamily(
    Font(R.font.michroma_regular, FontWeight.Normal),
)
