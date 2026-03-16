package org.nudgealarm.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// === 16-BIT MEGADRIVE COLOR SCHEME ===
// Always dark theme - like playing on a CRT in a dark room!

private val MegadriveColorScheme = darkColorScheme(
    // Primary - Sonic Blue for main actions
    primary = MegadriveBlue,
    onPrimary = Color.White,
    primaryContainer = MegadriveBlueDark,
    onPrimaryContainer = MegadriveBlueLight,

    // Secondary - Streets of Rage Purple
    secondary = MegadrivePurple,
    onSecondary = Color.White,
    secondaryContainer = MegadrivePurpleDark,
    onSecondaryContainer = Color.White,

    // Tertiary - Golden Axe Gold for highlights
    tertiary = MegadriveGold,
    onTertiary = MegadriveBlack,
    tertiaryContainer = MegadriveGoldDark,
    onTertiaryContainer = Color.White,

    // Error - Ring Loss Red
    error = MegadriveRed,
    onError = Color.White,
    errorContainer = MegadriveRedDark,
    onErrorContainer = Color.White,

    // Background - CRT Dark
    background = MegadriveBlack,
    onBackground = MegadriveTextPrimary,

    // Surface - Slightly lighter for cards
    surface = MegadriveSurface,
    onSurface = MegadriveTextPrimary,
    surfaceVariant = MegadriveSurfaceLight,
    onSurfaceVariant = MegadriveTextSecondary,

    // Outline
    outline = MegadriveTextSecondary,
    outlineVariant = MegadriveSurfaceLight,

    // Inverse (for snackbars etc)
    inverseSurface = MegadriveTextPrimary,
    inverseOnSurface = MegadriveBlack,
    inversePrimary = MegadriveBlueDark
)

@Composable
fun NudgeAlarmTheme(
    // Always use dark theme for that retro CRT feel
    darkTheme: Boolean = true,
    // Disable dynamic colors - we want our custom Megadrive palette!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = MegadriveColorScheme

    // Make status bar match our retro theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MegadriveBlack.toArgb()
            window.navigationBarColor = MegadriveBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
