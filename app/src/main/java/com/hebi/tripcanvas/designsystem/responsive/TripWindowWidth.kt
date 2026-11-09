package com.hebi.tripcanvas.designsystem.responsive

import androidx.compose.ui.unit.Dp

enum class TripWindowWidth {
    Compact,
    Medium,
    Expanded
}

fun classifyWindowWidth(widthDp: Int): TripWindowWidth = when {
    widthDp < 600 -> TripWindowWidth.Compact
    widthDp < 840 -> TripWindowWidth.Medium
    else -> TripWindowWidth.Expanded
}

fun classifyWindowWidth(width: Dp): TripWindowWidth =
    classifyWindowWidth(width.value.toInt())
