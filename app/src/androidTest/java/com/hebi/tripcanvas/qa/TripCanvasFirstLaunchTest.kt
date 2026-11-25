package com.hebi.tripcanvas.qa

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripCanvasFirstLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val navigator = QaUiNavigator(device)
    private val preferences by lazy { tripPreferencesRepository(context) }
    private val database by lazy { TripDatabase.get(context) }
    private val tripTitle = "QA first trip"

    @Test
    fun freshInstallWelcomeAndCreateTripUseRealScreens() {
        // Run only on a fresh, disposable emulator. Never erase stored trips to make this pass.
        assertFalse("Expected fresh onboarding state", runBlocking { preferences.preferences.first().onboardingCompleted })
        assertEquals("Expected an empty database", 0, runBlocking { database.backupDao().snapshot().totalRows })
        device.executeShellCommand("settings put system font_scale 1.0")
        runBlocking { preferences.saveThemeMode(TripThemeMode.Light) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitVisible(By.textContains("여행의 모든 순간을"), "welcome heading")
                capture("00-welcome-light")
                awaitVisible(By.text("여행 시작"), "welcome action")
                capture("00a-welcome-start-action")
                navigator.clickVisible(By.text("여행 시작"), "여행 시작", "내 여행")
                awaitVisible(By.text("등록된 여행이 없습니다"), "empty home")
                assertTrue(runBlocking { preferences.preferences.first().onboardingCompleted })
                capture("01-first-home-empty")
                navigator.clickVisible(By.text("새 여행 만들기"), "새 여행 만들기", "여행 기본 정보")
                awaitVisible(By.text("여행 기본 정보"), "create form")
                capture("02-create-trip-light")
                val startDate = By.textStartsWith("시작일 *")
                awaitVisible(startDate, "start date")
                navigator.clickVisible(startDate, "시작일", "취소")
                capture("02a-create-trip-date-picker")
                navigator.clickVisible(By.text("취소"), "날짜 선택 취소")
                awaitVisible(By.text("현지 설정"), "local settings")
                capture("02b-create-trip-local-settings")
                awaitVisible(By.text("여행 만들기"), "create submit")
                capture("02c-create-trip-bottom")
                navigator.clickVisible(By.text("여행 만들기"), "빈 여행 제목 제출")
                awaitVisible(By.text("여행 제목은 1~80자로 입력해 주세요."), "required title validation")
                assertEquals(0, runBlocking { database.backupDao().snapshot().trips.size })
                capture("02d-create-trip-required-title")
                awaitVisible(By.text("여행 기본 정보"), "top of create form")
                val field = device.findObjects(By.clazz("android.widget.EditText"))
                    .filter { it.visibleBounds.height() > 0 }
                    .minByOrNull { it.visibleBounds.top }
                    ?: throw AssertionError("Trip title field not visible")
                field.setText(tripTitle)
                awaitVisible(By.text(tripTitle), "entered trip title")
                awaitVisible(By.text("여행 만들기"), "create submit")
                navigator.clickVisible(By.text("여행 만들기"), "여행 만들기", "여행 한눈에")
                awaitVisible(By.text("여행 한눈에"), "new workspace")
                val trips = runBlocking { database.backupDao().snapshot().trips }
                assertEquals(1, trips.size)
                assertEquals(tripTitle, trips.single().title)
                capture("03-new-trip-workspace")
                device.pressBack()
                awaitVisible(By.text("내 여행"), "return to home")
                awaitVisible(By.text(tripTitle), "saved trip on home")
                capture("04-first-created-trip-home")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitVisible(By.text("내 여행"), "reopened home")
                awaitVisible(By.text(tripTitle), "persisted trip")
                assertTrue(runBlocking { preferences.preferences.first().onboardingCompleted })
                assertFalse(device.hasObject(By.text("여행 시작")))
                capture("05-reopened-home")
            }
        } catch (failure: Throwable) {
            runCatching {
                capture("99-first-launch-failure")
                device.dumpWindowHierarchy(File(output(), "99-first-launch-hierarchy.xml"))
            }
            throw failure
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun awaitVisible(selector: BySelector, label: String) {
        if (device.wait(Until.hasObject(selector), 6000)) return
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(14) {
                try {
                    val scroll = device.findObject(By.scrollable(true))
                    if (scroll != null) scroll.scroll(direction, 0.65f)
                    else {
                        val width = device.displayWidth
                        val height = device.displayHeight
                        val from = if (direction == Direction.DOWN) height * 3 / 4 else height / 3
                        val to = if (direction == Direction.DOWN) height / 3 else height * 3 / 4
                        device.swipe(width / 2, from, width / 2, to, 25)
                    }
                } catch (_: StaleObjectException) {
                    SystemClock.sleep(200)
                }
                device.waitForIdle()
                if (device.wait(Until.hasObject(selector), 1000)) return
            }
        }
        throw AssertionError("Missing real UI element: $label")
    }

    private fun output(): File = File(context.getExternalFilesDir(null), "qa").also {
        assertTrue(it.exists() || it.mkdirs())
    }

    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(400)
        assertTrue("Screenshot failed: $name", device.takeScreenshot(File(output(), "$name.png")))
    }
}
