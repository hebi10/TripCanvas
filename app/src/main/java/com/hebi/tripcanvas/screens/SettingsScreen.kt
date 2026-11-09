package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripDivider
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame

@Composable
fun SettingsScreen(
    themeMode: TripThemeMode,
    themeSaving: Boolean,
    themeSaveFailed: Boolean,
    onThemeModeChange: (TripThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenWorkspacePreview: () -> Unit,
    onOpenDesignSystemPreview: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val p = TripDesign.palette
    TripPageFrame(
        title = "설정",
        onBack = onBack
    ) {
        TripSectionHeading(
            title = "화면 모드",
            supportingText = "선택한 테마는 이 기기에 저장되며 앱을 다시 실행해도 유지됩니다."
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            TripThemeMode.entries.forEach { mode ->
                TripChoiceChip(
                    label = mode.label,
                    selected = themeMode == mode,
                    onClick = { if (!themeSaving) onThemeModeChange(mode) }
                )
            }
        }
        if (themeSaving) {
            Text(
                text = "테마 설정 저장 중",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
        if (themeSaveFailed) {
            TripErrorState(
                title = "테마를 저장하지 못했습니다",
                description = "선택을 다시 시도해 주세요."
            )
        }
        TripDivider()
        TripSectionHeading(
            title = "여행 기본값",
            supportingText = "여행 생성 시 기기 시간대와 기본 통화를 제안하며, 여행별로 수정할 수 있습니다."
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "새 여행 화면에서 여행별 통화와 현지 시간대를 변경할 수 있습니다.",
                modifier = Modifier.padding(TripSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
        TripDivider()
        TripSectionHeading(
            title = "데이터 관리",
            supportingText = "여행·장소·일정·경비·예산·준비물을 파일로 백업하고 복원할 수 있습니다."
        )
        TripSecondaryButton(
            label = "로컬 백업·복원",
            onClick = onOpenBackup,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "백업 파일은 암호화되지 않으므로 안전한 위치에 보관해야 합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.textSecondary
        )
        TripDivider()
        TripSectionHeading(
            title = "화면 검수",
            supportingText = "개발 단계의 디자인을 확인하는 미리보기입니다. 실제 여행 데이터는 생성되지 않습니다."
        )
        Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
            TripSecondaryButton(
                label = "여행 작업 공간 미리보기",
                onClick = onOpenWorkspacePreview,
                modifier = Modifier.fillMaxWidth()
            )
            TripSecondaryButton(
                label = "디자인 시스템 미리보기",
                onClick = onOpenDesignSystemPreview,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "TripCanvas · Android 초기 버전 0.1.0",
            style = MaterialTheme.typography.labelMedium,
            color = p.textSecondary
        )
    }
}
