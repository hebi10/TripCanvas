package com.hebi.tripcanvas.designsystem.responsive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TripAdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable (TripWindowWidth) -> Unit
) {
    BoxWithConstraints(modifier) {
        content(classifyWindowWidth(maxWidth))
    }
}
