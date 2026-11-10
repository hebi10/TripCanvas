package com.hebi.tripcanvas.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalTripPalette = staticCompositionLocalOf { LightTripPalette }

object TripDesign {
    val palette: TripPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalTripPalette.current
}

@Composable
fun isTripDarkMode(mode: TripThemeMode): Boolean = when (mode) {
    TripThemeMode.System -> isSystemInDarkTheme()
    TripThemeMode.Light -> false
    TripThemeMode.Dark -> true
}

@Composable
fun TripCanvasTheme(
    mode: TripThemeMode = TripThemeMode.System,
    content: @Composable () -> Unit
) {
    val dark = isTripDarkMode(mode)
    val palette = if (dark) DarkTripPalette else LightTripPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.primaryAction,
            onPrimary = palette.onPrimaryAction,
            primaryContainer = palette.surfaceSubtle,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.textSecondary,
            onSecondary = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.textSecondary,
            surfaceDim = palette.surfaceSubtle,
            surfaceBright = palette.surface,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surfaceSubtle,
            surfaceContainerHigh = palette.surfaceSubtle,
            surfaceContainerHighest = palette.surfaceSubtle,
            surfaceTint = palette.primaryAction,
            outline = palette.outline,
            outlineVariant = palette.outline,
            error = palette.error,
            onError = palette.background
        )
    } else {
        lightColorScheme(
            primary = palette.primaryAction,
            onPrimary = palette.onPrimaryAction,
            primaryContainer = palette.surfaceSubtle,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.textSecondary,
            onSecondary = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.textSecondary,
            surfaceDim = palette.surfaceSubtle,
            surfaceBright = palette.surface,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surfaceSubtle,
            surfaceContainerHigh = palette.surfaceSubtle,
            surfaceContainerHighest = palette.surfaceSubtle,
            surfaceTint = palette.primaryAction,
            outline = palette.outline,
            outlineVariant = palette.outline,
            error = palette.error,
            onError = palette.background
        )
    }
    CompositionLocalProvider(LocalTripPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TripTypography,
            shapes = TripShapes,
            content = content
        )
    }
}
