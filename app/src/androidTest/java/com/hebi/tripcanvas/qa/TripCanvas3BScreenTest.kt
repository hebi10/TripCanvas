package com.hebi.tripcanvas.qa

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.local.BookingEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ExpenseShareEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripCanvas3BScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun financeParticipantsAndBookingScreensAreNavigable() {
        device.executeShellCommand("settings put system font_scale 1.0")
        val db = TripDatabase.get(context)
        runBlocking {
            tripPreferencesRepository(context).completeOnboarding()
            tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Light)
            db.backupDao().replaceAll(
                TripBackupSnapshot(
                    trips = listOf(TripEntity(
                        "qa3b", "QA 제주 여행 3B", "2026-11-10", "2026-11-12",
                        "대한민국", "제주", "Asia/Seoul", "KRW", "", "환율·정산 QA", 1L, 1L
                    )),
                    places = emptyList(), itineraries = emptyList(),
                    expenses = listOf(ExpenseEntity(
                        "qa3b-exp", "qa3b", "10.00", "USD",
                        "2026-11-11T09:30:00", "FOOD", "점심 식사", null,
                        1L, 1L, "qa3b-person1", "KRW", "1320", "2026-11-10", "사용자 입력"
                    )),
                    budgets = emptyList(), checklist = emptyList(),
                    participants = listOf(
                        TripParticipantEntity("qa3b-person1", "qa3b", "민재", 1L, 1L),
                        TripParticipantEntity("qa3b-person2", "qa3b", "현지", 2L, 2L)
                    ),
                    shares = listOf(
                        ExpenseShareEntity("qa3b-exp", "qa3b-person1", 6600L, "KRW"),
                        ExpenseShareEntity("qa3b-exp", "qa3b-person2", 6600L, "KRW")
                    ),
                    bookings = listOf(BookingEntity(
                        "qa3b-book", "qa3b", "LODGING", "숙소 예약",
                        "2026-11-11T14:00", "QA-PRIVATE-REF", "체크인 예정", 1L, 1L
                    ))
                )
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText("QA 제주 여행 3B")
                capture("01-home-light")
                click("QA 제주 여행 3B")
                awaitText("동행자 열기")
                capture("02-workspace-light")
                click("동행자 열기")
                awaitText("로컬 동행자 2명")
                capture("03-participants-light")
                click("동행자 추가")
                awaitText("동행자 이름 *")
                capture("04-participant-editor")
                click("취소")
                device.pressBack()
                awaitText("정산 열기")
                click("정산 열기")
                awaitText("정산 제안 1건")
                capture("05-settlement-light")
                click("현재 정산 내역 확인")
                awaitText("정산 완료 확인")
                capture("06-settlement-confirmed")
                click("10.00 USD · 환율·분담 설정")
                awaitText("환율 스냅샷")
                capture("07-expense-split")
                device.pressBack()
                awaitText("동행자 정산")
                device.pressBack()
                awaitText("예약 열기")
                click("예약 열기")
                awaitText("예약 1건")
                capture("08-bookings-light")
                click("예약 상세·편집")
                awaitText("예약 확인번호")
                capture("09-booking-edit")
                click("PDF·이미지 첨부")
                val picked = device.wait(
                    Until.gone(By.pkg("com.hebi.tripcanvas")), 7000
                )
                assertTrue("Document picker did not open", picked)
                capture("10-document-picker")
                device.pressBack()
                awaitText("예약 편집")
                device.pressBack()
                awaitText("예약 자료")
                device.pressBack()
                awaitText("예약 열기")

                runBlocking {
                    tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark)
                }
                awaitText("예약 열기")
                capture("11-workspace-dark")
                click("정산 열기")
                awaitText("정산 제안 1건")
                capture("12-settlement-dark")
                device.executeShellCommand("settings put system font_scale 1.6")
                SystemClock.sleep(1200)
                scenario.recreate()
                awaitText("동행자 정산")
                capture("13-settlement-font160")
            }
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun click(text: String) {
        awaitText(text)
        SystemClock.sleep(300)
        val node = device.findObject(By.text(text))
            ?: throw AssertionError("Missing click target: " + text)
        node.click()
        device.waitForIdle()
        SystemClock.sleep(300)
    }

    private fun dismissSystemAnr() {
        val popup = device.findObject(By.textContains("isn't responding"))
            ?: device.findObject(By.textContains("응답하지"))
            ?: return
        val close = device.findObject(By.text("Close app"))
            ?: device.findObject(By.text("앱 닫기"))
        if (close != null) close.click() else device.pressBack()
        device.waitForIdle()
        SystemClock.sleep(400)
    }

    private fun awaitText(text: String) {
        dismissSystemAnr()
        if (device.wait(Until.hasObject(By.text(text)), 8000)) return
        repeat(7) {
            dismissSystemAnr()
            swipeUp()
            if (device.wait(Until.hasObject(By.text(text)), 1100)) return
        }
        repeat(7) {
            dismissSystemAnr()
            swipeDown()
            if (device.wait(Until.hasObject(By.text(text)), 1100)) return
        }
        capture("99-failure")
        device.dumpWindowHierarchy(File(output(), "99-hierarchy.xml"))
        throw AssertionError("Expected UI text missing: " + text)
    }

    private fun swipeUp() = device.swipe(
        device.displayWidth / 2, device.displayHeight * 3 / 4,
        device.displayWidth / 2, device.displayHeight / 3, 25
    )

    private fun swipeDown() = device.swipe(
        device.displayWidth / 2, device.displayHeight / 3,
        device.displayWidth / 2, device.displayHeight * 3 / 4, 25
    )

    private fun output(): File = File(
        context.getExternalFilesDir(null), "qa3b"
    ).also { assertTrue(it.exists() || it.mkdirs()) }

    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(400)
        assertTrue(device.takeScreenshot(File(output(), name + ".png")))
    }
}
