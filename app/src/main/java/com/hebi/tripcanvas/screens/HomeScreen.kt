package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame

private enum class TripListFilter(val label: String, val emptyLabel: String) {
    All("전체", "등록된 여행이 없습니다"),
    Upcoming("예정", "예정된 여행이 없습니다"),
    Active("여행 중", "진행 중인 여행이 없습니다"),
    Completed("완료", "완료된 여행이 없습니다")
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    var selectedFilter by rememberSaveable { mutableStateOf(TripListFilter.All) }
    val p = TripDesign.palette

    TripPageFrame(
        title = "TripCanvas",
        trailingActions = {
            TextButton(onClick = onOpenSettings) {
                Text("설정", color = p.textPrimary)
            }
        }
    ) {
        TripSectionHeading(
            title = "내 여행",
            supportingText = "여행별 일정을 정리할 공간입니다."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            TripListFilter.entries.forEach { filter ->
                TripChoiceChip(
                    label = filter.label,
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter }
                )
            }
        }
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            TripEmptyState(
                title = selectedFilter.emptyLabel,
                description = "실제 여행 생성 및 로컬 목록 저장은 2-A 단계에서 연결됩니다."
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
            TripPrimaryButton(
                label = "새 여행",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
            Text(
                text = "여행 생성 기능 준비 중 · 2-A 구현 예정",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
    }
}
