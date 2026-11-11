package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.designsystem.tokens.TripSizes

@Composable
fun TripEmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = TripDesign.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(TripSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = p.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = p.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            TripPrimaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun TripErrorState(
    title: String,
    description: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actionLabel: String = "다시 시도"
) {
    val p = TripDesign.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(TripSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.error)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        if (onRetry != null) {
            TripSecondaryButton(label = actionLabel, onClick = onRetry)
        }
    }
}

@Composable
fun TripLoadingState(
    label: String = "불러오는 중",
    modifier: Modifier = Modifier
) {
    val p = TripDesign.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(TripSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TripSpacing.md)
    ) {
        CircularProgressIndicator(color = p.primaryAction)
        Text(label, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TripOfflineNotice(
    description: String = "오프라인 상태입니다. 저장한 여행 정보는 계속 사용할 수 있습니다.",
    modifier: Modifier = Modifier
) {
    val p = TripDesign.palette
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = p.surfaceSubtle,
        shape = RectangleShape,
        border = BorderStroke(1.dp, p.outline)
    ) {
        Text(
            description,
            modifier = Modifier.padding(TripSpacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = p.textPrimary
        )
    }
}

@Composable
fun TripConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val p = TripDesign.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.heightIn(min = TripSizes.minimumTouchTarget)
            ) {
                Text(confirmLabel, color = if (destructive) p.error else p.primaryAction)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.heightIn(min = TripSizes.minimumTouchTarget)
            ) {
                Text("취소", color = p.textSecondary)
            }
        },
        shape = RectangleShape,
        containerColor = p.surface
    )
}
