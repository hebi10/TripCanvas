package com.hebi.tripcanvas.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TripPalette(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val primaryAction: Color,
    val onPrimaryAction: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

internal val LightTripPalette = TripPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF5F5F5),
    textPrimary = Color(0xFF171717),
    textSecondary = Color(0xFF626262),
    outline = Color(0xFFD2D2D2),
    primaryAction = Color(0xFF1F1F1F),
    onPrimaryAction = Color(0xFFFFFFFF),
    success = Color(0xFF256B45),
    warning = Color(0xFF8A5A00),
    error = Color(0xFFB3261E)
)

internal val DarkTripPalette = TripPalette(
    background = Color(0xFF111111),
    surface = Color(0xFF191919),
    surfaceSubtle = Color(0xFF242424),
    textPrimary = Color(0xFFF3F3F3),
    textSecondary = Color(0xFFB8B8B8),
    outline = Color(0xFF555555),
    primaryAction = Color(0xFFEFEFEF),
    onPrimaryAction = Color(0xFF161616),
    success = Color(0xFF9BD8B0),
    warning = Color(0xFFFFD088),
    error = Color(0xFFFFB4AB)
)
