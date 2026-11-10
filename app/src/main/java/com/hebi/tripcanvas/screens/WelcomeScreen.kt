package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    isSaving: Boolean,
    saveFailed: Boolean
) {
    val p = TripDesign.palette
    val compactHeading = LocalDensity.current.fontScale >= 1.4f
    Surface(modifier = Modifier.fillMaxSize(), color = p.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(TripSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xl)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.lg)
            ) {
                Text(
                    text = "TRIPCANVAS / WELCOME",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textSecondary
                )
                Spacer(modifier = Modifier.height(TripSpacing.lg))
                Text(
                    text = "여행의 모든 순간을\n하나의 캔버스에.",
                    style = if (compactHeading) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayLarge,
                    color = p.textPrimary
                )
                Text(
                    text = "여행의 일정부터 장소, 경비와 기록까지 하나의 여행 공간에서 관리할 수 있도록 준비합니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = p.textSecondary
                )
                Text(
                    text = "처음에는 로그인이나 위치 권한 없이 시작합니다. 여행 데이터는 기기에 저장됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                if (saveFailed) {
                    TripErrorState(
                        title = "시작 정보를 저장하지 못했습니다",
                        description = "저장에 실패했습니다. 다시 시도해 주세요."
                    )
                }
                TripPrimaryButton(
                    label = if (isSaving) "저장 중" else "여행 시작",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )
            }
        }
    }
}
