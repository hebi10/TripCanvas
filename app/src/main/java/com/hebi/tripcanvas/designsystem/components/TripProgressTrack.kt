package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing

@Composable
fun TripProgressTrack(
    fraction: Float,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val p = TripDesign.palette
    val value = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TripSpacing.xs)
            .background(p.outline)
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .fillMaxHeight()
                .background(if (warning) p.warning else p.textPrimary)
        )
    }
}
