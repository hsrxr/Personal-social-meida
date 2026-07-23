package com.journal.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Amber80,
    onSecondary = Amber40,
    tertiary = Sky80,
    onTertiary = Sky40,
    error = ErrorRed,
    onError = ErrorRedDark,
    surface = SurfaceDark,
    onSurface = Gray90,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Gray80,
    background = Gray10,
    onBackground = Gray90,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Green90,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Amber40,
    onSecondary = Amber80,
    tertiary = Sky40,
    onTertiary = Sky80,
    error = ErrorRedDark,
    onError = ErrorRed,
    surface = Gray99,
    onSurface = Gray10,
    surfaceVariant = Gray95,
    onSurfaceVariant = Gray30,
    background = Gray99,
    onBackground = Gray10,
)

@Composable
fun JournalTheme(
    darkTheme: Boolean = true, // default dark: matches glasses usage context
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JournalTypography,
        content = content,
    )
}
