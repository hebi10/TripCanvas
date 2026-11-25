package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.network.QueryRateLimitedException
import com.hebi.tripcanvas.data.search.PhotonPlaceSearch
import com.hebi.tripcanvas.data.search.RemotePlace
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TripPlaceSearchScreen(
    tripId: String,
    repository: PlaceRepository,
    searchService: PhotonPlaceSearch,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val palette = TripDesign.palette
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RemotePlace>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var savingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    TripPageFrame(title = "외부 장소 검색", onBack = onBack) {
        TripSectionHeading(
            title = "장소명·주소 검색",
            supportingText = if (searchService.available) {
                "검색 버튼을 누를 때만 개발용 Photon 서버에 검색어를 전송합니다."
            } else {
                "운영 장소 검색 공급자가 아직 연결되지 않았습니다."
            }
        )
        TripTextField(
            value = query,
            onValueChange = {
                query = it
                error = null
                searched = false
                results = emptyList()
            },
            label = "검색어",
            placeholder = "예: 제주국제공항"
        )
        TripPrimaryButton(
            label = if (searching) "검색 중" else "장소 검색",
            modifier = Modifier.fillMaxWidth(),
            enabled = searchService.available && !searching && savingId == null && query.trim().length in 2..80,
            onClick = {
                if (searchService.available && !searching) {
                    val submitted = query.trim()
                    searching = true
                    searched = false
                    error = null
                    results = emptyList()
                    scope.launch {
                        try {
                            val found = searchService.search(submitted)
                            if (query.trim() == submitted) {
                                results = found
                                searched = true
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (limited: QueryRateLimitedException) {
                            if (query.trim() == submitted) error = limited.message
                        } catch (_: Exception) {
                            if (query.trim() == submitted) {
                                error = "장소 검색을 완료하지 못했습니다. 인터넷 연결을 확인하거나 직접 등록해 주세요."
                            }
                        } finally {
                            searching = false
                        }
                    }
                }
            }
        )
        Text(
            if (searchService.available) {
                "검색 출처: OpenStreetMap · Photon(komoot). 공개 서버는 개발용이며 조회 실패 또는 요청 제한 시 직접 장소 등록을 이용할 수 있습니다."
            } else {
                "배포용 앱에서는 공개 데모 서버를 사용하지 않습니다. 이전 화면에서 저장 장소를 확인하거나 직접 등록해 주세요."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        if (searching) TripLoadingState("장소 검색 중")
        if (error != null) TripErrorState(
            title = "장소 검색 오류",
            description = requireNotNull(error),
            onRetry = { error = null }
        )
        if (searched && results.isEmpty()) {
            TripEmptyState("검색 결과 없음", "검색어를 바꾸거나 저장 장소에서 직접 등록해 주세요.")
        }
        if (results.isNotEmpty()) {
            TripSectionHeading("검색 결과 " + results.size + "건", "저장 전에 이름과 주소를 확인해 주세요.")
            results.forEach { place ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                    ) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(
                            place.address.ifBlank { "주소 없음 · 좌표 제공" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Text(
                            "위도 " + place.latitude + " · 경도 " + place.longitude,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        TripSecondaryButton(
                            label = if (savingId == place.providerId) "저장 중" else "내 여행에 저장",
                            enabled = !searching && savingId == null,
                            onClick = {
                                savingId = place.providerId
                                error = null
                                scope.launch {
                                    try {
                                        val saved = repository.importRemote(tripId, place)
                                        onSaved(saved.id)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failed: Exception) {
                                        error = "저장에 실패했습니다. 다시 시도해 주세요."
                                    } finally {
                                        savingId = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
