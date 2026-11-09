package com.hebi.tripcanvas.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            status = "파일 저장을 취소했습니다."
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
            status = "파일 선택을 취소했습니다."
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
            supportingText = "여행, 저장 장소, 일정, 준비물, 지출, 총예산을 구조화된 JSON 파일로 저장합니다."
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
                    "백업 파일은 암호화되지 않습니다. 개인정보와 지출 메모가 포함될 수 있으므로 " +
                        "안전한 저장 위치를 선택하고 파일을 타인과 공유하지 마세요.",
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
            supportingText = "먼저 파일을 읽고 버전·필수 데이터·여행 간 연결 관계를 검증합니다."
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(
                    "복원을 승인하면 현재 기기의 모든 여행·장소·일정·경비·예산·준비물이 " +
                        "백업 파일 내용으로 교체됩니다. 복원 전에 기존 데이터를 내보내세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textPrimary
                )
                Text(
                    "현재 로컬 자료 이외의 로그인·테마 설정·첨부파일은 백업에 포함되지 않습니다.",
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
            Text(it, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
        }
        error?.let {
            TripErrorState(
                title = "백업·복원 오류",
                description = it,
                onRetry = { error = null }
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
                "개, 준비물 " + snapshot.checklist.size + "개.\n\n" +
                "현재 기기의 여행 " + existing.size + "개 및 연결된 데이터를 모두 " +
                "삭제한 뒤 이 파일로 교체합니다. 기존 백업이 없다면 복구할 수 없습니다. 진행하시겠습니까?",
            confirmLabel = if (busy) "복원 중" else "전체 교체 및 복원",
            destructive = true,
            onDismiss = { if (!busy) pending = null },
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
