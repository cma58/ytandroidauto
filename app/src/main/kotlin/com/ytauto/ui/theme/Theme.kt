package com.ytauto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Kleurenpalet voor YTAuto.
 * Donker thema als standaard — past bij een muziek-/media-app.
 */

// ── Kleuren ──
private val Red400 = Color(0xFFEF5350)
private val Red600 = Color(0xFFE53935)
private val RedDark = Color(0xFFB71C1C)
private val Surface = Color(0xFF121212)
private val SurfaceVariant = Color(0xFF1E1E1E)
private val OnSurface = Color(0xFFE0E0E0)
private val OnSurfaceVariant = Color(0xFF9E9E9E)

private val DarkColorScheme = darkColorScheme(
    primary = Red400,
    onPrimary = Color.White,
    primaryContainer = RedDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF80CBC4),
    onSecondary = Color.Black,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = Color(0xFF444444),
)

private val LightColorScheme = lightColorScheme(
    primary = Red600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = RedDark,
    secondary = Color(0xFF00897B),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF616161),
)

@Composable
fun YTAutoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Maak de statusbar en navigatiebar transparant
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
