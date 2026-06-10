package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Handheld Forge palette ────────────────────────────────────────────────────
// Dynamic color is OFF — the app has a consistent identity regardless of wallpaper.

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
    background             = Color(0xFF0D1117),
    onBackground           = Color(0xFFE8EDF2),
    surface                = Color(0xFF161C24),
    onSurface              = Color(0xFFE8EDF2),
    surfaceVariant         = Color(0xFF1E2733),
    onSurfaceVariant       = Color(0xFF8F9EB0),
    outline                = Color(0xFF2C3A4A),
    outlineVariant         = Color(0xFF1E2733),
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

// ── Typography: default font-family with strong hierarchical weights/sizes ────
// Space Grotesk is not bundled (no TTF on disk, no downloadable-fonts dep).
// The hierarchy is enforced via bold weights + larger display sizes so the
// app still reads distinctly from a default Material app.
private val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun IsItCompatibleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamicColor is permanently OFF — the app has a fixed "Handheld Forge" identity.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkPalette else LightPalette

    // Edge-to-edge insets are handled by the Activity via enableEdgeToEdge().
    // We don't manually paint statusBarColor — deprecated in API 35.
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
