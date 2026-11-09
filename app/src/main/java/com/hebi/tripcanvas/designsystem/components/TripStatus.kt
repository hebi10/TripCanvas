package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing

enum class TripStatusTone {
    Neutral,
    Success,
    Warning,
    Error
}

@Composable
fun TripStatusTag(
    label: String,
    tone: TripStatusTone = TripStatusTone.Neutral
) {
    val palette = TripDesign.palette
    val contentColor = when (tone) {
        TripStatusTone.Neutral -> palette.textPrimary
        TripStatusTone.Success -> palette.success
        TripStatusTone.Warning -> palette.warning
        TripStatusTone.Error -> palette.error
    }
    val container = when (tone) {
        TripStatusTone.Neutral -> palette.surfaceSubtle
        else -> contentColor.copy(alpha = 0.12f)
    }
    Surface(color = container, shape = RectangleShape) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = TripSpacing.sm, vertical = TripSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}
