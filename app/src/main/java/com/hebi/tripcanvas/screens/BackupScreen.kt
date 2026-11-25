package com.hebi.tripcanvas.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.backup.BackupPasswordRequired
import com.hebi.tripcanvas.data.backup.EncryptedTripBackup
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
import com.hebi.tripcanvas.designsystem.components.TripTextField
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
    var exportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    DisposableEffect(Unit) {
        onDispose { exportPassword?.fill('\u0000') }
    }
    BackHandler(enabled = busy) {}

    fun saveBackup(uri: Uri?, encrypted: Boolean, password: CharArray?) {
        if (uri == null) {
            password?.fill('\u0000')
            error = null
            status = "파일 저장을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다."
            return
        }
        if (busy || (encrypted && password == null)) {
            password?.fill('\u0000')
            error = "백업을 다시 시작해 주세요. 암호화 비밀번호 없이 파일을 저장하지 않습니다."
            return
        }
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val saved = if (encrypted) service.exportEncrypted(uri, requireNotNull(password))
                    else service.export(uri)
                status = "백업 완료: 여행 " + saved.trips.size + "개, 전체 자료 " +
                    saved.totalRows + "건"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                error = "백업 데이터 용량 또는 비밀번호를 확인해 주세요."
            } catch (_: Exception) {
                error = "파일 저장에 실패했습니다. 저장 공간과 접근 권한을 확인하고 불완전한 백업 파일은 사용하지 마세요."
            } finally {
                password?.fill('\u0000')
                busy = false
            }
        }
    }

    fun previewBackup(uri: Uri, password: CharArray? = null) {
        if (busy) {
            password?.fill('\u0000')
            return
        }
        busy = true
        error = null
        status = null
        pending = null
        scope.launch {
            try {
                pending = service.preview(uri, password)
                status = "복원할 내용을 확인한 뒤 최종 적용을 선택해 주세요."
            } catch (_: BackupPasswordRequired) {
                importUri = uri
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                error = "파일을 열지 못했습니다. 파일 접근 권한을 확인해 주세요."
            } catch (failed: IllegalArgumentException) {
                error = if (failed.message?.contains("허용 범위") == true) {
                    "파일 크기가 10MB 제한을 초과했습니다."
                } else {
                    "비밀번호가 올바르지 않거나 백업 파일이 손상되었거나 지원하지 않는 버전입니다."
                }
            } catch (_: Exception) {
                error = "백업 파일을 검증하지 못했습니다."
            } finally {
                password?.fill('\u0000')
                busy = false
            }
        }
    }

    val encryptedPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = exportPassword
        exportPassword = null
        saveBackup(uri, encrypted = true, password = password)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> saveBackup(uri, encrypted = false, password = null) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            error = null
            status = "파일 선택을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다."
        } else {
            previewBackup(uri)
        }
    }

    if (exportDialog) {
        BackupPasswordDialog(
            exporting = true,
            onDismiss = { exportDialog = false },
            onConfirm = { password ->
                exportDialog = false
                exportPassword?.fill('\u0000')
                exportPassword = password
                encryptedPicker.launch("TripCanvas_backup_" + LocalDate.now().toString() + ".tcb")
            }
        )
    }
    importUri?.let { uri ->
        BackupPasswordDialog(
            exporting = false,
            onDismiss = {
                importUri = null
                status = "복원을 취소했습니다. 기존 여행 데이터는 유지됩니다."
            },
            onConfirm = { password ->
                importUri = null
                previewBackup(uri, password)
            }
        )
    }

    TripPageFrame(title = "백업·복원", onBack = { if (!busy) onBack() }) {
        TripSectionHeading(
            title = "로컬 데이터 백업",
            supportingText = "1단계 · 여행·일정·경비·정산·예약·기록 데이터를 비밀번호로 보호하여 내보냅니다."
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
                    "암호화 백업은 비밀번호를 알아야 복원할 수 있습니다. 비밀번호를 잊으면 복구할 수 없습니다. " +
                        "평문 JSON은 예약 확인번호·동행자 정보가 노출될 수 있으므로 안전하게 보관하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                TripPrimaryButton(
                    label = "암호화 백업 내보내기",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { exportDialog = true }
                )
                TripSecondaryButton(
                    label = "평문 JSON 내보내기",
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
                    "암호화 백업과 기존 JSON 백업을 지원합니다. 파일 선택만으로 기존 데이터는 바뀌지 않습니다. " +
                        "앱 테마·예약 문서 및 사진 원본은 포함되지 않으므로 원본을 별도로 보관하세요.",
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


@Composable
private fun BackupPasswordDialog(
    exporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length in EncryptedTripBackup.MIN_PASSWORD_LENGTH..EncryptedTripBackup.MAX_PASSWORD_LENGTH &&
        password.any { !it.isWhitespace() } && (!exporting || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exporting) "암호화 백업 비밀번호 설정" else "암호화 백업 비밀번호 입력") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(if (exporting) "12~128자의 충분히 긴 비밀번호를 설정하세요. 분실 시 복원할 수 없습니다."
                    else "백업을 생성할 때 설정한 비밀번호를 입력해 주세요.")
                TripTextField(
                    value = password,
                    onValueChange = { if (it.length <= EncryptedTripBackup.MAX_PASSWORD_LENGTH) password = it },
                    label = "백업 비밀번호",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (exporting) {
                    TripTextField(
                        value = confirmation,
                        onValueChange = { if (it.length <= EncryptedTripBackup.MAX_PASSWORD_LENGTH) confirmation = it },
                        label = "비밀번호 확인",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmation.isNotEmpty() && confirmation != password
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val selected = password.toCharArray()
                password = ""
                confirmation = ""
                onConfirm(selected)
            }) { Text(if (exporting) "저장 위치 선택" else "비밀번호 확인 및 검증") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
