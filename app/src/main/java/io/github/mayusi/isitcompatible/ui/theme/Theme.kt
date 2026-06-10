package io.github.mayusi.isitcompatible.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF82B1FF),
    secondary = Color(0xFFB39DDB),
    tertiary = Color(0xFF80CBC4),
    background = Color(0xFF101216),
    surface = Color(0xFF181B20),
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF5E35B1),
    tertiary = Color(0xFF00897B),
)

@Composable
fun IsItCompatibleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkPalette
        else -> LightPalette
    }

    // Edge-to-edge insets are now handled by the Activity via enableEdgeToEdge().
    // We don't manually paint statusBarColor anymore — deprecated in API 35.
    MaterialTheme(colorScheme = colorScheme, content = content)
}
