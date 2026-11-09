package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.responsive.TripAdaptiveContainer
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame

@Composable
fun TripWorkspaceScreen(
    onBack: () -> Unit,
    isPreview: Boolean
) {
    val p = TripDesign.palette
    TripPageFrame(
        title = "여행 작업 공간",
        onBack = onBack
    ) {
        if (!isPreview) {
            TripEmptyState(
                title = "여행 데이터를 아직 연결할 수 없습니다",
                description = "여행 조회 및 상세 데이터 연결은 2-A 단계에서 구현됩니다."
            )
        } else {
            TripStatusTag(label = "화면 미리보기")
            TripSectionHeading(
                title = "여행의 모든 정보를 한곳에",
                supportingText = "이 화면은 레이아웃 미리보기입니다. 여행 제목, 날짜, 지출 등 실제 데이터는 없습니다."
            )
            Text(
                text = "여행 개요 · 일정 · 지출 · 준비물의 정보 구조만 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
            TripAdaptiveContainer(modifier = Modifier.fillMaxWidth()) { width ->
                if (width == TripWindowWidth.Compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.md)) {
                        WorkspacePanel("일정", "날짜별 여행 일정")
                        WorkspacePanel("경비", "여행의 지출 내역")
                        WorkspacePanel("준비물", "출발 전 체크리스트")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TripSpacing.md)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(TripSpacing.md)
                        ) {
                            WorkspacePanel("일정", "날짜별 여행 일정")
                            WorkspacePanel("준비물", "출발 전 체크리스트")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            WorkspacePanel("경비", "여행의 지출 내역")
                        }
                    }
                }
            }
            Text(
                text = "실제 데이터가 연결되면 2단계부터 해당 영역을 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun WorkspacePanel(title: String, description: String) {
    val p = TripDesign.palette
    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        }
    }
}
