package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes

@Composable
fun TripPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = TripDesign.palette
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = TripSizes.minimumTouchTarget),
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.primaryAction,
            contentColor = p.onPrimaryAction,
            disabledContainerColor = p.surfaceSubtle,
            disabledContentColor = p.textSecondary
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TripSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = TripDesign.palette
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TripSizes.minimumTouchTarget),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, p.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = p.textPrimary,
            disabledContentColor = p.textSecondary
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TripChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = TripDesign.palette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier.heightIn(min = TripSizes.minimumTouchTarget),
        shape = RectangleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = p.surface,
            labelColor = p.textSecondary,
            selectedContainerColor = p.primaryAction,
            selectedLabelColor = p.onPrimaryAction
        )
    )
}
