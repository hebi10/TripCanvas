package com.hebi.tripcanvas.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripDivider
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripOfflineNotice
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripStatusTone
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.responsive.TripAdaptiveContainer
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing

@Composable
fun DesignSystemScreen(
    themeMode: TripThemeMode,
    onThemeModeChange: (TripThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val palette = TripDesign.palette
    Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TripSizes.compactGutter, vertical = TripSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = TripSizes.maximumContentWidth),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.lg)
            ) {
                Text(
                    "TRIPCANVAS / 01-A",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
                TripSecondaryButton(
                    label = "설정으로 돌아가기",
                    onClick = onBack
                )
                TripSectionHeading(
                    title = "여행을 위한 하나의 캔버스",
                    supportingText = "Travel Editorial 디자인 시스템 미리보기 — 여행 관리 기능은 다음 단계에서 연결됩니다."
                )
                TripDivider()
                TripSectionHeading(
                    title = "색상 및 테마",
                    supportingText = "선택한 테마는 설정에 저장되며 앱을 다시 실행해도 유지됩니다."
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
                ) {
                    TripThemeMode.entries.forEach { mode ->
                        TripChoiceChip(
                            label = mode.label,
                            selected = mode == themeMode,
                            onClick = { onThemeModeChange(mode) }
                        )
                    }
                }
                TripAdaptiveContainer(modifier = Modifier.fillMaxWidth()) { width ->
                    if (width == TripWindowWidth.Compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.md)) {
                            PreviewComponents()
                            PreviewStates()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TripSpacing.lg)
                        ) {
                            Column(modifier = Modifier.weight(1f)) { PreviewComponents() }
                            Column(modifier = Modifier.weight(1f)) { PreviewStates() }
                        }
                    }
                }
                TripDivider()
                Text(
                    "01-A · 스타일 및 공통 컴포넌트 확인용 화면",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun PreviewComponents() {
    val palette = TripDesign.palette
    var sampleTitle by rememberSaveable { mutableStateOf("") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TripSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.md)
        ) {
            TripSectionHeading(
                title = "공통 컨트롤",
                supportingText = "모든 여행 화면에서 재사용하는 기본 스타일입니다."
            )
            TripTextField(
                value = sampleTitle,
                onValueChange = { sampleTitle = it },
                label = "여행 제목",
                placeholder = "여행 이름 입력",
                supportingText = "입력값은 이 미리보기에서만 표시됩니다."
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                TripPrimaryButton(label = "확인 예시", onClick = { showDialog = true })
                TripSecondaryButton(label = "보조 버튼", onClick = { showDialog = true })
            }
            Text(
                "예정 · 진행 · 완료",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                TripStatusTag(label = "기본")
                TripStatusTag(label = "완료", tone = TripStatusTone.Success)
                TripStatusTag(label = "주의", tone = TripStatusTone.Warning)
                TripStatusTag(label = "오류", tone = TripStatusTone.Error)
            }
        }
    }
    if (showDialog) {
        TripConfirmDialog(
            title = "확인 대화상자 예시",
            message = "여행 데이터를 변경하지 않습니다. 공통 다이얼로그 스타일만 확인합니다.",
            confirmLabel = "확인",
            onConfirm = { showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun PreviewStates() {
    var showError by rememberSaveable { mutableStateOf(false) }

    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TripSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.md)
        ) {
            TripSectionHeading(
                title = "화면 상태",
                supportingText = "빈 상태 · 오류 · 오프라인 안내를 분리합니다."
            )
            TripEmptyState(
                title = "등록된 여행이 없습니다",
                description = "새 여행을 만들면 일정과 경비를 한곳에서 관리할 수 있습니다."
            )
            TripDivider()
            if (showError) {
                TripErrorState(
                    title = "화면 예시: 저장 실패",
                    description = "입력값은 보존되며 다시 시도할 수 있습니다.",
                    onRetry = { showError = false }
                )
            } else {
                TripSecondaryButton(
                    label = "오류 상태 미리보기",
                    onClick = { showError = true }
                )
            }
            TripOfflineNotice(
                description = "상태 예시: 네트워크 없이도 로컬 저장 데이터는 사용할 수 있습니다."
            )
        }
    }
}
