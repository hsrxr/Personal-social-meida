package com.journal.app.ui.theme

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
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue30,
    secondary = Blue40,
    onSecondary = Color.White,
    secondaryContainer = Blue90,
    onSecondaryContainer = Blue30,
    tertiary = Sky40,
    onTertiary = Color.White,
    error = ErrorRedDark,
    onError = Color.White,
    surface = NeutralSurface,
    onSurface = OnNeutral,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = OnNeutralVariant,
    background = NeutralBackground,
    onBackground = OnNeutral,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
)

@Composable
fun JournalTheme(
    darkTheme: Boolean = false, // default light: matches the Echoes phone-app mockup
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
