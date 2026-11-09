package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TripDeleteDialog(
    trip: TripEntity,
    onDelete: suspend (String) -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var confirmation by rememberSaveable(trip.id) { mutableStateOf("") }
    var deleting by remember(trip.id) { mutableStateOf(false) }
    var failed by remember(trip.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("여행 삭제", color = p.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
                Text(
                    "여행과 연결된 일정·저장 장소도 함께 삭제됩니다. 복구할 백업이 없다면 되돌릴 수 없습니다. 여행명을 정확히 입력해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                Text(trip.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it; failed = false },
                    label = { Text("여행명 확인") },
                    singleLine = true,
                    enabled = !deleting
                )
                if (failed) {
                    Text("여행을 삭제하지 못했습니다. 다시 시도해 주세요.", color = p.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting && confirmation == trip.title,
                onClick = {
                    deleting = true
                    failed = false
                    scope.launch {
                        try {
                            onDelete(trip.id)
                            onDeleted()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failed = true
                        } finally {
                            deleting = false
                        }
                    }
                }
            ) {
                Text(if (deleting) "삭제 중" else "삭제", color = p.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !deleting, onClick = onDismiss) {
                Text("취소", color = p.textSecondary)
            }
        },
        shape = RectangleShape,
        containerColor = p.surface
    )
}
