package com.hebi.tripcanvas.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.backup.TripBackupCodec
import com.hebi.tripcanvas.data.backup.TripBackupService
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(
    database: TripDatabase,
    tripRepository: TripRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val service = remember(context, database) {
        TripBackupService(context.applicationContext, database)
    }
    val scope = rememberCoroutineScope()
    val p = TripDesign.palette
    val existing by remember(tripRepository) { tripRepository.trips }
        .collectAsState(initial = emptyList())
    var pending by remember { mutableStateOf<TripBackupSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = busy) {}

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            error = null
            status = "파일 저장을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다."
        } else if (!busy) {
            busy = true
            error = null
            status = null
            scope.launch {
                try {
                    val saved = service.export(uri)
                    status = "백업 완료: 여행 " + saved.trips.size + "개, 전체 자료 " +
                        saved.totalRows + "건"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: IllegalArgumentException) {
                    error = "백업 파일이 10MB를 초과했습니다. 데이터 양을 확인해 주세요."
                } catch (failed: Exception) {
                    error = "파일 저장에 실패했습니다. 사용 가능한 저장 공간과 접근 권한을 확인해 주세요."
                } finally {
                    busy = false
                }
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            error = null
            status = "파일 선택을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다."
        } else if (!busy) {
            busy = true
            error = null
            status = null
            pending = null
            scope.launch {
                try {
                    pending = service.preview(uri)
                    status = "복원할 내용을 확인한 뒤 최종 적용을 선택해 주세요."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: IOException) {
                    error = "파일을 열지 못했습니다. 파일 접근 권한을 확인해 주세요."
                } catch (failed: IllegalArgumentException) {
                    error = if (failed.message?.contains("허용 범위") == true) {
                        "파일 크기가 10MB 제한을 초과했습니다."
                    } else {
                        "백업 파일이 손상되었거나 지원하지 않는 버전입니다."
                    }
                } catch (failed: Exception) {
                    error = "백업 파일이 손상되었거나 지원되지 않는 버전입니다."
                } finally {
                    busy = false
                }
            }
        }
    }

    TripPageFrame(title = "백업·복원", onBack = { if (!busy) onBack() }) {
        TripSectionHeading(
            title = "로컬 데이터 백업",
            supportingText = "1단계 · 여행·장소·일정·준비물·경비·예산·동행자·정산·예약 정보를 JSON 파일로 내보냅니다."
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(
                    "현재 기기의 여행 " + existing.size + "개",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.textPrimary
                )
                Text(
                    "백업 형식 v" + TripBackupCodec.VERSION +
                        " · 최대 " + (TripBackupCodec.MAX_BYTES / 1024 / 1024) + "MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                Text(
                    "백업 파일은 암호화되지 않습니다. 예약 확인번호·동행자 이름·메모가 " +
                        "포함될 수 있습니다. 안전하게 보관하고 파일 공유에 주의하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                TripPrimaryButton(
                    label = "백업 파일 내보내기",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        exportPicker.launch(
                            "TripCanvas_backup_" + LocalDate.now().toString() + ".json"
                        )
                    }
                )
            }
        }
        TripSectionHeading(
            title = "백업 복원",
            supportingText = "2단계 · 파일 내용을 미리 검증한 뒤 전체 데이터 교체를 최종 승인합니다."
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(
                    "복원을 승인하면 현재 기기의 여행·장소·일정·경비·예산·준비물·동행자·예약이 " +
                        "백업 파일 내용으로 교체됩니다. 복원 전에 기존 데이터를 내보내세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textPrimary
                )
                Text(
                    "파일 선택만으로 기존 데이터가 바뀌지 않습니다. 앱 테마·예약 문서 원본·파일 URI는 " +
                        "백업에서 제외되므로 복원 후 문서를 다시 연결해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                TripSecondaryButton(
                    label = "백업 파일 선택 및 검증",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        importPicker.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    }
                )
            }
        }
        if (busy) TripLoadingState("파일을 처리하는 중")
        status?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = p.textPrimary
            )
        }
        error?.let {
            TripErrorState(
                title = "백업·복원 오류",
                description = it,
                onRetry = { error = null },
                actionLabel = "닫기"
            )
        }
    }

    pending?.let { snapshot ->
        TripConfirmDialog(
            title = "현재 데이터 전체 교체",
            message = "파일 검증 완료: 여행 " + snapshot.trips.size +
                "개, 장소 " + snapshot.places.size +
                "개, 일정 " + snapshot.itineraries.size +
                "개, 경비 " + snapshot.expenses.size +
                "개, 예산 " + snapshot.budgets.size +
                "개, 준비물 " + snapshot.checklist.size +
                "개, 동행자 " + snapshot.participants.size +
                "명, 분담 " + snapshot.shares.size +
                "건, 예약 " + snapshot.bookings.size + "건.\n\n" +
                "현재 기기의 여행 " + existing.size + "개 및 연결된 데이터를 모두 " +
                "삭제한 뒤 이 파일로 교체합니다. 기존 백업이 없다면 복구할 수 없습니다. 진행하시겠습니까?",
            confirmLabel = if (busy) "복원 중" else "전체 교체 및 복원",
            destructive = true,
            enabled = !busy,
            onDismiss = {
                if (!busy) {
                    pending = null
                    status = "복원을 취소했습니다. 기존 여행 데이터는 유지됩니다."
                }
            },
            onConfirm = {
                if (!busy) {
                    busy = true
                    error = null
                    status = null
                    scope.launch {
                        try {
                            service.restore(snapshot)
                            pending = null
                            status = "복원이 완료되었습니다. 여행 목록에 새로운 데이터가 반영됐습니다."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = "복원에 실패했습니다. 기존 데이터는 변경되지 않았습니다."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
    }
}
