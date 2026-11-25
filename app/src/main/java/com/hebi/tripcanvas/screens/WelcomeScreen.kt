package com.hebi.tripcanvas.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.R
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
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
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(R.drawable.ic_tripcanvas_launcher_foreground),
                    contentDescription = null, modifier = Modifier.size(88.dp))
                Text(
                    text = "TripCanvas",
                    style = MaterialTheme.typography.headlineMedium,
                    color = p.textPrimary
                )
                Spacer(modifier = Modifier.height(TripSpacing.sm))
                Text(
                    text = "여행의 모든 순간을\n하나의 캔버스에.",
                    style = if (compactHeading) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayLarge,
                    color = p.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "일정부터 장소, 경비와 기록까지 하나의 여행 공간에서 관리하세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = p.textSecondary,
                    textAlign = TextAlign.Center
                )
                val features = listOf("여행 일정", "경비·정산", "준비물", "예약 자료", "여행 기록", "지도·장소")
                features.chunked(if (compactHeading) 1 else 2).forEach { items ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                        items.forEach { feature ->
                            TripSurfacePanel(modifier = Modifier.weight(1f)) {
                                Text(feature, modifier = Modifier.padding(TripSpacing.sm),
                                    style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                            }
                        }
                    }
                }
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
