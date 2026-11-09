package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing

@Composable
fun TripSurfacePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val p = TripDesign.palette
    Surface(
        modifier = modifier,
        shape = RectangleShape,
        color = p.surface,
        border = BorderStroke(1.dp, p.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content
    )
}

@Composable
fun TripSectionHeading(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val p = TripDesign.palette
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(TripSpacing.xxs)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
        if (supportingText != null) {
            Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        }
    }
}

@Composable
fun TripDivider(modifier: Modifier = Modifier) {
    val p = TripDesign.palette
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = p.outline)
}
