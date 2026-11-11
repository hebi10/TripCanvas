package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.BookingType
import com.hebi.tripcanvas.data.local.BookingEntity
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TripBookingsScreen(
    tripId: String,
    repository: BookingRepository,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit
) {
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val bookings by remember(repository, tripId) {
        repository.observe(tripId)
    }.collectAsState(initial = emptyList())
    var deleteTarget by remember { mutableStateOf<BookingEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TripPageFrame(title = "예약 자료", onBack = onBack) {
        TripSectionHeading(
            title = "예약 " + bookings.size + "건",
            supportingText = "항공·숙소·교통·입장권 예약 정보를 보관합니다. 실제 예약·결제는 제공하지 않습니다."
        )
        TripPrimaryButton(
            label = "예약 추가",
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreate
        )
        if (bookings.isEmpty()) {
            TripEmptyState("등록된 예약이 없습니다", "예약 정보를 직접 추가해 주세요.")
        }
        bookings.forEach { booking ->
            key(booking.id) {
                BookingListCard(
                    booking = booking,
                    repository = repository,
                    busy = busy,
                    onEdit = { onEdit(booking.id) },
                    onDelete = { deleteTarget = booking }
                )
            }
        }
        if (error != null) {
            TripErrorState("예약 작업 오류", requireNotNull(error), onRetry = { error = null })
        }
    }
    deleteTarget?.let { target ->
        TripConfirmDialog(
            title = "예약 자료 삭제",
            message = target.title + " 예약과 연결한 파일 참조를 삭제하시겠습니까? " +
                "기기에 있거나 외부에 저장된 원본 파일은 삭제되지 않습니다.",
            confirmLabel = "예약 삭제",
            destructive = true,
            onDismiss = { if (!busy) deleteTarget = null },
            onConfirm = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            repository.delete(tripId, target.id)
                            deleteTarget = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = "예약 정보를 삭제할 수 없습니다."
                        } finally { busy = false }
                    }
                }
            }
        )
    }
}

@Composable
private fun BookingListCard(
    booking: BookingEntity,
    repository: BookingRepository,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = TripDesign.palette
    val attachments by remember(repository, booking.id) {
        repository.attachments(booking.id)
    }.collectAsState(initial = emptyList())

    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            Text(
                BookingType.valueOf(booking.type).label,
                style = MaterialTheme.typography.labelLarge,
                color = palette.textSecondary
            )
            Text(
                booking.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "예약 일정 · " + bookingDateLabel(booking.startAtLocal),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Text(
                bookingAttachmentLabel(attachments.size),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            if (booking.referenceNumber.isNotBlank()) {
                Text(
                    "확인번호 등록됨 · 목록에서 비공개",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            TripSecondaryButton(
                label = "예약 상세·편집",
                onClick = onEdit
            )
            TripSecondaryButton(
                label = "예약 삭제",
                enabled = !busy,
                onClick = onDelete
            )
        }
    }
}
