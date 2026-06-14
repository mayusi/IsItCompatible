package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
// AppTypography → Type.kt | AppMaterialShapes → Shape.kt | both in same package

// ── Handheld Forge palette ────────────────────────────────────────────────────
// Dynamic color is OFF — the app has a consistent identity regardless of wallpaper.
//
// Dark palette refinements vs. v1:
//  • background deepened #0D1117 → #0A0E14 for more contrast against surface cards
//  • surface kept at #161C24 — now has clearly more lift from the deeper background
//  • surfaceVariant bumped to #1F2A38 — creates readable tier separation:
//      background (#0A0E14) < surface (#161C24) < surfaceVariant (#1F2A38)
//    Previously surface == surfaceVariant = #1E2733 made cards invisible on each other.
//  • outlineVariant darkened slightly (#1A2330) to remain a subtle divider on surface

private val DarkPalette = darkColorScheme(
    primary                = Color(0xFF6EE7F7), // electric cyan
    onPrimary              = Color(0xFF003740),
    primaryContainer       = Color(0xFF004E5C),
    onPrimaryContainer     = Color(0xFFB3F0F8),
    secondary              = Color(0xFF9ECAFF),
    onSecondary            = Color(0xFF003258),
    secondaryContainer     = Color(0xFF00467C),
    onSecondaryContainer   = Color(0xFFD4E3FF),
    tertiary               = Color(0xFF86EFAC), // green
    onTertiary             = Color(0xFF003918),
    tertiaryContainer      = Color(0xFF005225),
    onTertiaryContainer    = Color(0xFFA7F3C7),
    background             = Color(0xFF0A0E14), // ← deepened for card contrast
    onBackground           = Color(0xFFE8EDF2),
    surface                = Color(0xFF161C24), // cards sit here
    onSurface              = Color(0xFFE8EDF2),
    surfaceVariant         = Color(0xFF1F2A38), // ← bumped; now visually distinct from surface
    onSurfaceVariant       = Color(0xFF8F9EB0),
    outline                = Color(0xFF2C3A4A),
    outlineVariant         = Color(0xFF1A2330), // ← subtle divider on surface
    error                  = Color(0xFFFF6B6B),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
)

private val LightPalette = lightColorScheme(
    primary                = Color(0xFF006A7A), // deep teal
    onPrimary              = Color(0xFFFFFFFF),
    primaryContainer       = Color(0xFFB3EFF8),
    onPrimaryContainer     = Color(0xFF002029),
    secondary              = Color(0xFF004C8A),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFD4E3FF),
    onSecondaryContainer   = Color(0xFF001B3A),
    tertiary               = Color(0xFF1B6B3A),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFA7F3C7),
    onTertiaryContainer    = Color(0xFF00210E),
    background             = Color(0xFFF5F7FA),
    onBackground           = Color(0xFF0D1117),
    surface                = Color(0xFFFFFFFF),
    onSurface              = Color(0xFF0D1117),
    surfaceVariant         = Color(0xFFE8EDF2),
    onSurfaceVariant       = Color(0xFF3A4A5C),
    outline                = Color(0xFF7A8A9C),
    outlineVariant         = Color(0xFFBCC8D8),
    error                  = Color(0xFFBA1A1A),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
)

// Typography is defined in Type.kt (AppTypography) and references ChakraPetch
// from Fonts.kt. Both live in this same package — no import needed.

@Composable
fun IsItCompatibleTheme(
    // The app is always dark — a fixed dark "gamer HUD" identity regardless of the
    // system setting. The param is kept (defaulting true) so a future in-app
    // Dark/Light toggle could flip it without touching call sites.
    darkTheme: Boolean = true,
    // dynamicColor is permanently OFF — the app has a fixed "Handheld Forge" identity.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkPalette else LightPalette

    // Edge-to-edge insets are handled by the Activity via enableEdgeToEdge().
    // We don't manually paint statusBarColor — deprecated in API 35.
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,        // defined in Type.kt
        shapes      = AppMaterialShapes,    // defined in Shape.kt
        content     = content,
    )
}
