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

// ═══ Dark theme (unchanged) ═══
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

// ═══ Light theme: warm sunset palette — young / warm / loving ═══
private val LightColorScheme = lightColorScheme(
    // Primary: warm coral pink for buttons, active nav, links, match badges
    primary = CoralPrimary,
    onPrimary = WarmSurface,
    primaryContainer = CoralContainer,
    onPrimaryContainer = CoralOnContainer,

    // Secondary: golden amber for accents
    secondary = AmberPrimary,
    onSecondary = WarmSurface,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,

    // Tertiary: soft rose for message bubbles / special highlights
    tertiary = RosePrimary,
    onTertiary = WarmSurface,
    tertiaryContainer = RoseContainer,
    onTertiaryContainer = RoseOnContainer,

    // Error
    error = ErrorRedLight,
    onError = WarmSurface,
    errorContainer = ErrorContainerLight,

    // Surfaces
    surface = WarmSurface,
    onSurface = WarmOnNeutral,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnNeutralVariant,
    background = CreamBackground,
    onBackground = WarmOnNeutral,
    outline = WarmOutline,
    outlineVariant = WarmOutline,
)

@Composable
fun JournalTheme(
    darkTheme: Boolean = false,
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
