package com.hebi.tripcanvas.qa

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.local.BookingEntity
import com.hebi.tripcanvas.data.local.BookingAttachmentEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripCanvas3BScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val navigator = QaUiNavigator(device)

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
                        "qa3b", "제주 동행 여행", "2026-11-10", "2026-11-12",
                        "대한민국", "제주", "Asia/Seoul", "KRW", "", "동행자와 경비 정리", 1L, 1L
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
            db.bookingDao().addAttachment(
                BookingAttachmentEntity(
                    "qa3b-attachment", "qa3b-book",
                    "content://com.hebi.tripcanvas.invalid/booking.pdf",
                    "숙소 예약 확인서.pdf", "application/pdf", 1L
                )
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText("제주 동행 여행")
                capture("01-home-light")
                click("제주 동행 여행")
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
                clickWorkspaceCard("정산 열기", expectedScreenTitle = "정산 제안 1건")
                awaitText("정산 제안 1건")
                awaitText("현지 → 민재")
                awaitText("KRW 6,600")
                capture("05-settlement-light")
                awaitText("참가자별 지불·부담")
                capture("05a-settlement-member-detail")
                click("현재 정산 내역 확인", expectedAfter = "정산 완료 확인")
                awaitText("정산 완료 확인")
                capture("06-settlement-confirmed")
                click("10.00 USD · 환율·분담 설정", expectedAfter = "환율 스냅샷")
                awaitText("환율 스냅샷")
                capture("07-expense-split")
                device.pressBack()
                awaitText("동행자 정산")
                device.pressBack()
                awaitText("예약 열기")
                clickWorkspaceCard("예약 열기")
                awaitText("예약 1건")
                awaitText("예약 일정 · 2026.11.11 (수) 14:00")
                awaitText("첨부 1개")
                assertFalse("예약 확인번호가 목록에 노출되면 안 됩니다.", device.hasObject(By.text("QA-PRIVATE-REF")))
                capture("08-bookings-light")
                assertFalse(
                    "삭제는 예약 관리 메뉴에만 노출되어야 합니다.",
                    device.hasObject(By.text("예약 삭제"))
                )
                navigator.clickVisible(By.desc("예약 관리: 숙소 예약"), "예약 관리: 숙소 예약")
                awaitText("예약 삭제")
                capture("08a-booking-overflow-menu")
                click("예약 삭제", expectedAfter = "예약 자료 삭제")
                awaitText("예약 자료 삭제")
                capture("08b-booking-delete-confirm")
                click("취소")
                awaitText("숙소 예약")
                awaitText("예약 상세·편집")
                click("예약 상세·편집", expectedAfter = "예약 확인번호")
                awaitText("예약 확인번호")
                awaitText("확인번호 보기")
                awaitText("첨부 1개")
                awaitText("숙소 예약 확인서.pdf")
                capture("09-booking-edit")
                click("확인번호 보기", expectedAfter = "확인번호 숨기기")
                awaitText("확인번호 숨기기")
                capture("09a-booking-reference-visible")
                click("확인번호 숨기기", expectedAfter = "확인번호 보기")
                awaitText("확인번호 보기")
                click("파일 연결 제거", expectedAfter = "문서 연결 제거")
                awaitText("문서 연결 제거")
                capture("09b-document-detach-confirm")
                click("취소")
                assertEquals(1, runBlocking {
                    db.bookingDao().observeAttachments("qa3b-book").first().size
                })
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
                click("예약 열기")
                awaitText("예약 일정 · 2026.11.11 (수) 14:00")
                capture("11a-bookings-dark")
                device.pressBack()
                awaitText("예약 열기")
                clickWorkspaceCard("정산 열기", expectedScreenTitle = "정산 제안 1건")
                awaitText("정산 제안 1건")
                awaitText("현지 → 민재")
                awaitText("KRW 6,600")
                capture("12-settlement-dark")
                device.executeShellCommand("settings put system font_scale 1.6")
                SystemClock.sleep(1200)
                scenario.recreate()
                awaitText("동행자 정산")
                awaitText("정산 제안 1건")
                capture("13-settlement-font160")
                device.pressBack()
                awaitText("예약 열기")
                clickWorkspaceCard("예약 열기")
                awaitText("예약 1건")
                awaitText("첨부 1개")
                capture("14-bookings-dark-font160")
            }
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun uiSelector(text: String) =
        if (text.endsWith(" 열기") || text == "저장 장소 관리") By.desc(text) else By.text(text)

    private fun clickWorkspaceCard(label: String, expectedScreenTitle: String? = null) {
        val destination = expectedScreenTitle ?: if (label == "예약 열기") "예약 1건" else null
        repeat(12) {
            dismissSystemAnr()
            try {
                val card = device.wait(Until.findObject(By.desc(label)), 1400)
                if (card != null) {
                    val bounds = card.visibleBounds
                    val height = device.displayHeight
                    val completelyInside = bounds.width() > 0 &&
                        bounds.top >= height / 6 &&
                        bounds.bottom <= height * 5 / 6
                    if (completelyInside) {
                        if (label == "예약 열기") capture("07a-bookings-card-before-click")
                        card.click()
                        device.waitForIdle()
                        if (destination != null &&
                            !device.wait(Until.hasObject(By.text(destination)), 4000)
                        ) {
                            SystemClock.sleep(250)
                            return@repeat
                        }
                        if (label == "예약 열기") capture("07b-bookings-card-after-click")
                        return
                    }
                    if (bounds.centerY() > height / 2) swipeUp() else swipeDown()
                } else {
                    swipeUp()
                }
            } catch (_: StaleObjectException) {
                SystemClock.sleep(250)
            }
        }
        capture("07c-workspace-card-not-centered")
        throw AssertionError("작업 공간 카드를 열지 못했습니다: $label" +
            (destination?.let { " · 예상 화면: $it" } ?: ""))
    }

    private fun click(text: String, expectedAfter: String? = null) {
        awaitText(text)
        try {
            navigator.clickVisible(uiSelector(text), text, expectedAfter)
        } catch (failure: AssertionError) {
            capture("99-click-failure")
            runCatching { device.dumpWindowHierarchy(File(output(), "99-click-hierarchy.xml")) }
            throw failure
        }
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
        if (device.wait(Until.hasObject(uiSelector(text)), 8000)) return
        repeat(7) {
            dismissSystemAnr()
            swipeUp()
            if (device.wait(Until.hasObject(uiSelector(text)), 1100)) return
        }
        repeat(7) {
            dismissSystemAnr()
            swipeDown()
            if (device.wait(Until.hasObject(uiSelector(text)), 1100)) return
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
