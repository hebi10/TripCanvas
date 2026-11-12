package com.hebi.tripcanvas.qa

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.responsive.classifyWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripCanvas3AMapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val navigator = QaUiNavigator(device)

    @Test
    fun mapSavedPlaceAndItineraryNavigationUseRealScreens() {
        val db = TripDatabase.get(context)
        device.executeShellCommand("settings put system font_scale 1.0")
        runBlocking {
            tripPreferencesRepository(context).completeOnboarding()
            tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Light)
            db.backupDao().replaceAll(
                TripBackupSnapshot(
                    trips = listOf(
                        TripEntity(
                            "qa3a", "제주 장소 여행", "2026-11-10", "2026-11-12",
                            "대한민국", "제주", "Asia/Seoul", "KRW",
                            "", "가고 싶은 장소와 일정을 정리", 1L, 1L
                        )
                    ),
                    places = listOf(
                        PlaceEntity(
                            "qa3a-place", "qa3a", "제주국제공항", "제주시 공항로 2", "",
                            33.5104, 126.4914, 1L, 1L,
                            "Photon/OSM", "N:6789", 1_000L
                        ),
                        PlaceEntity(
                            "qa3a-no-coord", "qa3a", "좌표 없는 장소", "", "",
                            null, null, 1L, 1L
                        ),
                        PlaceEntity(
                            "qa3a-alternate", "qa3a", "제주도청", "제주시 문연로 6", "",
                            33.4888, 126.4975, 1L, 1L
                        )
                    ),
                    itineraries = listOf(
                        ItineraryItemEntity(
                            "qa3a-item", "qa3a", "2026-11-11", "공항 도착",
                            "10:00", "10:30", "qa3a-place", "", false,
                            true, 0, 1L, 1L
                        )
                    ),
                    expenses = emptyList(), budgets = emptyList(), checklist = emptyList()
                )
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText("제주 장소 여행")
                capture("01-home-light")
                click("제주 장소 여행")
                awaitText("지도 열기")
                capture("02-workspace-light")
                click("지도 열기")
                awaitText("여행 지도")
                SystemClock.sleep(3000)
                capture("03-map-all-light")
                val widthDp = (device.displayWidth / context.resources.displayMetrics.density).toInt()
                if (classifyWindowWidth(widthDp) != TripWindowWidth.Compact) {
                    val listHeader = device.wait(
                        Until.findObject(By.text("장소 3개")), 5000
                    ) ?: throw AssertionError("태블릿 장소 패널 헤더를 찾지 못했습니다.")
                    val mapBounds = Rect()
                    scenario.onActivity { activity ->
                        val mapView = findMapView(activity.window.decorView)
                            ?: throw AssertionError("태블릿 MapLibre 패널이 없습니다.")
                        assertTrue("태블릿 지도가 보이지 않습니다.", mapView.getGlobalVisibleRect(mapBounds))
                    }
                    assertTrue(
                        "태블릿 장소 목록이 지도 오른쪽에 표시되지 않았습니다.",
                        listHeader.visibleBounds.left >= mapBounds.right - 12
                    )
                    capture("03a-tablet-two-pane-map-and-list")
                }
                click("2026-11-11")
                SystemClock.sleep(1000)
                var tapX = 0
                var tapY = 0
                scenario.onActivity { activity ->
                    val mapView = findMapView(activity.window.decorView)
                        ?: throw AssertionError("MapLibre view missing from 3-A map screen")
                    val bounds = Rect()
                    assertTrue("MapLibre map is not visible", mapView.getGlobalVisibleRect(bounds))
                    val location = IntArray(2)
                    mapView.getLocationOnScreen(location)
                    tapX = location[0] + mapView.width / 2
                    tapY = location[1] + mapView.height / 2
                    assertTrue("MapLibre pin center is not visible", bounds.contains(tapX, tapY))
                    File(evidenceDir(), "03a-pin-tap-geometry.txt").writeText(
                        "Visible map: $bounds\nTap: $tapX,$tapY\n" +
                            "Full map: ${location[0]},${location[1]} ${mapView.width}x${mapView.height}\n"
                    )
                }
                device.click(tapX, tapY)
                awaitText("공급자: Photon/OSM")
                capture("04a-pin-tap-detail")
                device.pressBack()
                awaitText("여행 지도")
                awaitText("확정 · 일정 1개")
                awaitText("장소 1개")
                awaitText("제주국제공항")
                capture("04-map-date-filter")
                if (classifyWindowWidth(widthDp) == TripWindowWidth.Compact) {
                    click("지도에서 선택: 제주국제공항")
                    awaitText("지도 선택 · 제주국제공항")
                    capture("04b-map-focus-from-place-list")
                    click("선택 장소 상세")
                } else {
                    // The tablet's two-pane list has its own direct detail action.
                    // Camera focus is verified separately below with 제주도청.
                    capture("04b-tablet-place-list-before-detail")
                    click("장소 상세")
                }
                awaitText("공급자: Photon/OSM")
                capture("05-place-detail")
                click("일정 편집")
                awaitText("일정 제목 *")
                capture("06-linked-schedule")
                device.pressBack()
                awaitText("장소 상세")
                device.pressBack()
                awaitText("여행 지도")
                click("전체")
                awaitText("장소 3개")
                awaitText("좌표 없는 장소")
                awaitText("좌표 없음 · 지도 핀 제외")
                capture("06a-map-places-without-coordinates")
                click("지도에서 선택: 제주도청")
                assertMapCentered(scenario, 33.4888, 126.4975)
                capture("06b-map-second-place-camera-focused")
                click("외부 장소 검색")
                awaitText("검색어")
                capture("07-external-search-input")
                device.pressBack()
                awaitText("여행 지도")
                device.pressBack()
                awaitText("지도 열기")

                runBlocking {
                    tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark)
                }
                awaitText("지도 열기")
                click("지도 열기")
                awaitText("여행 지도")
                capture("08-map-dark")
                device.executeShellCommand("settings put system font_scale 1.6")
                SystemClock.sleep(2000)
                capture("09-map-font160")
            }
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun assertMapCentered(
        scenario: ActivityScenario<MainActivity>,
        expectedLatitude: Double,
        expectedLongitude: Double
    ) {
        repeat(16) {
            val latch = CountDownLatch(1)
            val center = AtomicReference<LatLng?>(null)
            scenario.onActivity { activity ->
                val view = findMapView(activity.window.decorView)
                    ?: throw AssertionError("MapLibre map view is missing")
                view.getMapAsync { map ->
                    center.set(map.cameraPosition.target)
                    latch.countDown()
                }
            }
            assertTrue("MapLibre camera callback timed out", latch.await(5, TimeUnit.SECONDS))
            val target = center.get()
            if (target != null &&
                abs(target.latitude - expectedLatitude) < 0.005 &&
                abs(target.longitude - expectedLongitude) < 0.005
            ) return
            SystemClock.sleep(200)
        }
        throw AssertionError("MapLibre camera did not focus the selected saved place")
    }

    private fun findMapView(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = findMapView(root.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun dismissLauncherAnr() {
        val error = device.findObject(By.textContains("isn't responding"))
            ?: device.findObject(By.textContains("응답하지"))
            ?: return
        val close = device.findObject(By.text("Close app"))
            ?: device.findObject(By.text("앱 닫기"))
        if (close != null) close.click() else device.pressBack()
        device.waitForIdle()
        SystemClock.sleep(500)
    }

    private fun awaitText(text: String) {
        dismissLauncherAnr()
        if (device.wait(Until.hasObject(uiSelector(text)), 8000)) return
        repeat(5) {
            dismissLauncherAnr()
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 25
            )
            if (device.wait(Until.hasObject(uiSelector(text)), 1500)) return
        }
        repeat(6) {
            dismissLauncherAnr()
            device.swipe(
                device.displayWidth / 2, device.displayHeight / 3,
                device.displayWidth / 2, device.displayHeight * 3 / 4, 25
            )
            if (device.wait(Until.hasObject(uiSelector(text)), 1500)) return
        }
        val dir = evidenceDir()
        device.takeScreenshot(File(dir, "99-failure.png"))
        device.dumpWindowHierarchy(File(dir, "99-hierarchy.xml"))
        throw AssertionError("Expected UI text missing: $text")
    }

    private fun uiSelector(text: String) =
        if (text.endsWith(" 열기") || text == "저장 장소 관리") By.desc(text) else By.text(text)

    private fun click(text: String) {
        awaitText(text)
        try {
            navigator.clickVisible(uiSelector(text), text)
        } catch (failure: AssertionError) {
            val dir = evidenceDir()
            device.takeScreenshot(File(dir, "99-click-failure.png"))
            runCatching { device.dumpWindowHierarchy(File(dir, "99-click-hierarchy.xml")) }
            throw failure
        }
    }

    private fun evidenceDir(): File =
        File(context.getExternalFilesDir(null), "qa3a").also {
            assertTrue(it.exists() || it.mkdirs())
        }

    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(400)
        assertTrue(device.takeScreenshot(File(evidenceDir(), "$name.png")))
    }
}
