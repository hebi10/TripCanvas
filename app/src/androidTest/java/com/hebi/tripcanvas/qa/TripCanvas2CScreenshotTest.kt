package com.hebi.tripcanvas.qa

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android emulator UI proof: runs the actual MainActivity, traverses the user's
 * screens, tests checklist CRUD and expense validation, and writes raw PNGs.
 * Does not use mocked Compose screens or generated marketing art.
 */
@RunWith(AndroidJUnit4::class)
class TripCanvas2CScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val database: TripDatabase by lazy { TripDatabase.get(context) }

    @Before
    fun prepareFixture() {
        device.executeShellCommand("settings put system font_scale 1.0")
        runBlocking {
            tripPreferencesRepository(context).completeOnboarding()
            tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Light)
            database.backupDao().replaceAll(
                TripBackupSnapshot(
                    trips = listOf(
                        TripEntity(
                            "qa-trip", "제주 주말 여행", "2026-11-20", "2026-11-22",
                            "대한민국", "제주", "Asia/Seoul", "KRW",
                            "2박 3일 여행", "여행 일정과 지출을 한눈에",
                            1L, 1L
                        )
                    ),
                    places = listOf(
                        PlaceEntity(
                            "qa-place", "qa-trip", "제주국제공항", "제주시 공항로 2",
                            "첫 방문 장소", 33.5104, 126.4914, 1L, 1L
                        )
                    ),
                    itineraries = listOf(
                        ItineraryItemEntity(
                            "qa-schedule", "qa-trip", "2026-11-20", "공항 도착",
                            "10:00", "10:30", "qa-place", "렌터카 수령",
                            true, true, 0, 1L, 1L
                        )
                    ),
                    expenses = listOf(
                        ExpenseEntity(
                            "qa-expense-krw", "qa-trip", "12500", "KRW",
                            "2026-11-20T12:30:00", "FOOD", "점심 식사",
                            "qa-place", 1L, 1L
                        ),
                        ExpenseEntity(
                            "qa-expense-usd", "qa-trip", "12.50", "USD",
                            "2026-11-20T14:30:00", "OTHER", "해외 결제",
                            null, 1L, 1L
                        )
                    ),
                    budgets = listOf(TripBudgetEntity("qa-trip", "300000", "KRW", 1L)),
                    checklist = listOf(
                        ChecklistItemEntity("qa-check-1", "qa-trip", "여권", true, 0, 1L, 1L),
                        ChecklistItemEntity("qa-check-2", "qa-trip", "충전기", false, 1, 1L, 1L),
                        ChecklistItemEntity("qa-check-3", "qa-trip", "상비약", false, 2, 1L, 1L)
                    )
                )
            )
        }
    }

    @After
    fun resetDisplay() {
        device.executeShellCommand("settings put system font_scale 1.0")
    }

    @Test
    fun captureRealMainActivityAndExerciseMvp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitText("제주 주말 여행")
            capture("01-home-light")
            assertStatusBarBackground(dark = false)
            click("완료")
            awaitText("완료된 여행이 없습니다")
            capture("01a-home-filter-empty")
            click("전체")
            awaitText("제주 주말 여행")
            openTripActions()
            capture("01b-home-actions-menu")
            click("여행 편집")
            awaitText("여행 기본 정보")
            awaitText("여행 기간·목적지")
            capture("01c-home-edit-from-menu")
            click("시작일 *  2026-11-20")
            awaitText("취소")
            capture("01c1-home-edit-date-picker")
            click("취소")
            awaitText("현지 설정")
            capture("01c2-home-edit-local-settings")
            device.pressBack()
            awaitText("내 여행")
            openTripActions()
            click("여행 삭제")
            awaitText("여행명 확인")
            capture("01d-home-delete-confirm")
            click("취소")
            awaitText("내 여행")
            click("제주 주말 여행")
            awaitText("여행 한눈에")
            capture("02a-workspace-overview-light")
            click("여행 설명 보기")
            awaitText("여행 일정과 지출을 한눈에")
            capture("02a1-workspace-details-light")
            click("여행 설명 접기")
            awaitText("작업 도구")
            swipeUp()
            awaitText("일정 열기")
            capture("02b-workspace-tools-light")
            capture("02d-schedule-before-click")
            click("일정 열기")
            capture("02d-schedule-after-click")
            awaitText("여행 일정")
            awaitText("날짜 선택")
            awaitText("공항 도착")
            capture("02d-workspace-schedule-route")
            click("11/21 토")
            awaitText("이 날짜에는 일정이 없습니다")
            capture("02f-schedule-empty-date")
            click("11/20 금")
            awaitText("공항 도착")
            val scheduleMenu = device.wait(
                Until.findObject(By.desc("공항 도착 일정 메뉴")), 5000
            ) ?: throw AssertionError("일정 카드 더보기 메뉴를 찾지 못했습니다.")
            scheduleMenu.click()
            awaitText("복제")
            capture("02g-schedule-actions-menu")
            click("복제")
            assertTrue(waitForScheduleCount(2))
            awaitText("시간 겹침")
            capture("02h-schedule-after-duplicate")
            device.wait(Until.findObject(By.desc("공항 도착 일정 메뉴")), 5000)
                ?.click() ?: throw AssertionError("복제 후 일정 메뉴를 찾지 못했습니다.")
            awaitText("아래로")
            click("아래로")
            assertTrue(waitForScheduleFirstId("qa-schedule", first = false))
            device.wait(Until.findObject(By.desc("공항 도착 일정 메뉴")), 5000)
                ?.click() ?: throw AssertionError("재정렬 후 일정 메뉴를 찾지 못했습니다.")
            click("삭제")
            awaitText("일정 삭제")
            capture("02i-schedule-delete-confirm")
            click("취소")
            assertTrue(waitForScheduleCount(2))
            device.pressBack()
            awaitText("작업 도구")
            awaitText("저장 장소 관리")
            capture("02c-workspace-tools-last-light")
            click("저장 장소 관리")
            awaitText("여행 장소")
            capture("02e-workspace-places-route")
            device.pressBack()
            awaitText("작업 도구")
            awaitText("다음 일정")
            awaitText("사용 경비")
            awaitText("KRW 12,500")
            awaitText("준비물 완료")
            awaitText("1 / 3")
            awaitText("경비 열기")
            capture("02-workspace-light")

            openScreen("준비물 열기", "체크 상태와 항목 순서는 기기에 저장됩니다.")
            awaitText("준비 완료 1 / 3")
            awaitText("여권")
            capture("03-checklist-light")
            click("미완료")
            capture("04-checklist-incomplete-filter")
            openChecklistActions("충전기")
            val moveWhileFiltered = device.wait(Until.findObject(By.text("위로")), 3000)
                ?: throw AssertionError("필터에서 재정렬 메뉴를 찾지 못했습니다.")
            capture("04a-checklist-filter-menu")
            val orderBeforeDisabledMove = runBlocking {
                database.checklistDao().ordered("qa-trip").map { it.id }
            }
            moveWhileFiltered.click()
            Thread.sleep(350)
            assertEquals(
                "미완료 필터에서 이동을 눌러도 저장 순서는 바뀌지 않아야 합니다.",
                orderBeforeDisabledMove,
                runBlocking { database.checklistDao().ordered("qa-trip").map { it.id } }
            )
            if (device.hasObject(By.text("수정"))) device.pressBack()
            click("전체")
            click("준비물 추가")
            awaitText("준비물 이름 *")
            capture("05-checklist-add-dialog")
            val textField = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")), 4000
            ) ?: throw AssertionError("준비물 입력 필드 접근성 노드를 찾지 못했습니다.")
            textField.setText("멀티탭")
            click("저장")
            assertTrue(waitForChecklistCount(4))
            capture("06-checklist-added")
            openChecklistActions("충전기")
            awaitText("수정")
            capture("06a-checklist-actions-menu")
            click("수정")
            awaitText("준비물 수정")
            capture("06b-checklist-edit-dialog")
            click("취소")
            openChecklistActions("충전기")
            click("아래로")
            assertTrue("아래로 이동 후 정렬 순서가 저장되어야 합니다.", waitForChecklistPosition("qa-check-2", 2))
            openChecklistActions("충전기")
            click("위로")
            assertTrue("위로 이동 후 정렬 순서가 복원되어야 합니다.", waitForChecklistPosition("qa-check-2", 1))
            val checkSelector = By.desc("충전기 준비 여부")
            device.wait(Until.findObject(checkSelector), 5000)
                ?.click() ?: throw AssertionError("준비물 체크박스의 접근성 이름이 없습니다.")
            assertTrue(waitForChecklistChecked("qa-check-2", true))
            device.wait(Until.findObject(checkSelector), 5000)
                ?.click() ?: throw AssertionError("변경 후 준비물 체크박스를 찾지 못했습니다.")
            assertTrue(waitForChecklistChecked("qa-check-2", false))
            openChecklistActions("멀티탭")
            click("삭제")
            awaitText("준비물 삭제")
            capture("06c-checklist-delete-confirm")
            click("삭제")
            assertTrue(waitForChecklistCount(3))
            click("방금 삭제한 준비물 복구")
            assertTrue(waitForChecklistCount(4))
            capture("06d-checklist-restored")
            device.pressBack()
            awaitText("준비물 열기")

            openScreen("경비 열기", "예산 설정·변경")
            awaitText("예산 현황")
            awaitText("총예산")
            awaitText("지출 합계")
            awaitText("잔액")
            awaitText("KRW 287,500 남음")
            capture("07-expenses-light")
            awaitText("환산 필요")
            capture("07a-expenses-unconverted-warning")
            click("예산 설정·변경")
            awaitText("총예산 · KRW")
            capture("08-budget-edit-dialog")
            click("취소")
            device.wait(Until.gone(By.text("총예산 · KRW")), 3000)
            openScreen("지출 추가", "지출 정보")
            awaitText("지출 일시")
            capture("09-expense-create-form")
            swipeUp()
            awaitText("지출 저장")
            capture("09a-expense-form-bottom")
            click("지출 저장")
            awaitText("0보다 큰 금액을 통화별 소수 자릿수에 맞게 입력해 주세요.")
            capture("10-expense-validation")
            assertEquals(2, runBlocking {
                database.expenseDao().observeForTrip("qa-trip").first().size
            })
            device.pressBack()
            awaitText("예산·경비")
            device.pressBack()
            awaitText("경비 열기")
            device.pressBack()
            awaitText("TripCanvas")

            click("설정")
            awaitText("로컬 백업·복원")
            capture("11-settings-light")
            click("로컬 백업·복원")
            awaitText("백업 파일 내보내기")
            capture("12-backup-restore-light")
            click("백업 파일 내보내기")
            val pickerOpened = (0 until 25).any {
                if (device.currentPackageName != "com.hebi.tripcanvas") true
                else { Thread.sleep(300); false }
            }
            assertTrue("Android document picker did not become foreground", pickerOpened)
            capture("13-android-file-save-picker")
            device.pressBack()
            awaitText("백업·복원")
            awaitText("파일 저장을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다.")
            assertTrue("백업 저장 취소 후 기존 여행이 유지되어야 합니다.", runBlocking {
                database.tripDao().findById("qa-trip") != null
            })
            capture("14-backup-cancelled")
            click("백업 파일 선택 및 검증")
            val importPickerOpened = (0 until 25).any {
                if (device.currentPackageName != "com.hebi.tripcanvas") true
                else { Thread.sleep(300); false }
            }
            assertTrue("Android import document picker did not become foreground", importPickerOpened)
            capture("14a-backup-import-picker")
            device.pressBack()
            awaitText("백업·복원")
            awaitText("파일 선택을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다.")
            assertTrue("백업 선택 취소 후 기존 여행이 유지되어야 합니다.", runBlocking {
                database.tripDao().findById("qa-trip") != null
            })
            capture("14b-backup-import-cancelled")
            device.pressBack()
            awaitText("설정")
            device.pressBack()
            awaitText("TripCanvas")

            runBlocking {
                tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark)
            }
            device.waitForIdle()
            awaitText("내 여행")
            capture("15-home-dark")
            assertStatusBarBackground(dark = true)

            click("제주 주말 여행")
            awaitText("여행 한눈에")
            capture("16a-workspace-overview-dark")
            awaitText("작업 도구")
            capture("16b-workspace-tools-dark")
            awaitText("준비물 완료")
            awaitText("1 / 4")
            awaitText("경비 열기")
            capture("16-workspace-dark")
            openScreen("준비물 열기", "체크 상태와 항목 순서는 기기에 저장됩니다.")
            awaitText("준비 완료 1 / 4")
            awaitText("멀티탭")
            capture("17-checklist-dark")
            device.pressBack()
            awaitText("경비 열기")
            openScreen("경비 열기", "예산 설정·변경")
            awaitText("예산 현황")
            capture("18-expenses-dark")
            device.pressBack()
            awaitText("경비 열기")
            device.pressBack()
            awaitText("TripCanvas")
            click("설정")
            awaitText("로컬 백업·복원")
            capture("18a-settings-dark")
            click("로컬 백업·복원")
            awaitText("백업 파일 내보내기")
            capture("19-backup-dark")
            device.pressBack()
            awaitText("설정")
            device.pressBack()
            awaitText("TripCanvas")

            device.executeShellCommand("settings put system font_scale 1.6")
            Thread.sleep(2000)
            scenario.recreate()
            awaitText("내 여행")
            Thread.sleep(1200)
            capture("20-home-dark-font160")
            assertStatusBarBackground(dark = true)
            click("설정")
            awaitText("화면 모드")
            awaitText("개인정보·앱 정보")
            capture("20a-settings-dark-font160")
            device.pressBack()
            awaitText("TripCanvas")
            click("제주 주말 여행")
            awaitText("여행 한눈에")
            capture("21a-workspace-overview-dark-font160")
            awaitText("작업 도구")
            capture("21b-workspace-tools-dark-font160")
            awaitText("경비 열기")
            capture("21-workspace-dark-font160")
            openScreen("준비물 열기", "체크 상태와 항목 순서는 기기에 저장됩니다.")
            awaitText("준비 완료 1 / 4")
            awaitText("여권")
            capture("22-checklist-dark-font160")
            openChecklistActions("충전기")
            capture("22a-checklist-menu-dark-font160")
            device.pressBack()
            device.pressBack()
            awaitText("경비 열기")
            openScreen("경비 열기", "예산 설정·변경")
            awaitText("예산 현황")
            capture("23-expenses-dark-font160")
        }
    }

    private fun assertStatusBarBackground(dark: Boolean) {
        val screenshot = File(context.cacheDir, "qa-status-bar.png")
        assertTrue("Status bar screenshot failed", device.takeScreenshot(screenshot))
        val bitmap = BitmapFactory.decodeFile(screenshot.absolutePath)
            ?: throw AssertionError("Status bar screenshot could not be decoded")
        try {
            val safeY = (context.resources.displayMetrics.density * 10).toInt()
                .coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(bitmap.width / 2, safeY)
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            assertTrue(
                "Status bar background does not match ${if (dark) "dark" else "light"} theme (brightness=$brightness)",
                if (dark) brightness < 100 else brightness > 155
            )
        } finally {
            bitmap.recycle()
            screenshot.delete()
        }
    }

    private fun openTripActions() {
        val button = device.wait(
            Until.findObject(By.desc("제주 주말 여행 메뉴")), 5000
        ) ?: throw AssertionError("여행 카드 더보기 버튼을 찾지 못했습니다.")
        button.click()
        awaitText("여행 편집")
        awaitText("여행 삭제")
    }

    private fun waitForScheduleCount(expected: Int): Boolean {
        repeat(20) {
            val size = runBlocking {
                database.itineraryDao().getDay("qa-trip", "2026-11-20").size
            }
            if (size == expected) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun waitForScheduleFirstId(id: String, first: Boolean): Boolean {
        repeat(20) {
            val actual = runBlocking {
                database.itineraryDao().getDay("qa-trip", "2026-11-20").firstOrNull()?.id
            }
            if ((actual == id) == first && actual != null) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun openChecklistActions(itemTitle: String) {
        awaitText(itemTitle)
        val action = device.wait(
            Until.findObject(By.desc("$itemTitle 준비물 메뉴")), 5000
        ) ?: throw AssertionError("준비물 더보기 버튼을 찾지 못했습니다: $itemTitle")
        action.click()
        awaitText("수정")
    }

    private fun waitForChecklistPosition(id: String, position: Int): Boolean {
        repeat(20) {
            val actual = runBlocking {
                database.checklistDao().ordered("qa-trip").indexOfFirst { it.id == id }
            }
            if (actual == position) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun waitForChecklistChecked(id: String, checked: Boolean): Boolean {
        repeat(20) {
            val actual = runBlocking { database.checklistDao().find("qa-trip", id)?.checked }
            if (actual == checked) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun waitForChecklistCount(expected: Int): Boolean {
        repeat(20) {
            val size = runBlocking {
                database.checklistDao().observeForTrip("qa-trip").first().size
            }
            if (size == expected) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun dismissSystemAnr() {
        val popup = device.findObject(By.textContains("isn't responding"))
            ?: device.findObject(By.textContains("응답하지"))
            ?: return
        val close = device.findObject(By.text("Close app"))
            ?: device.findObject(By.text("앱 닫기"))
        if (close != null) close.click() else device.pressBack()
        device.waitForIdle()
        Thread.sleep(400)
    }

    private fun awaitText(text: String) {
        dismissSystemAnr()
        if (device.wait(Until.hasObject(uiSelector(text)), 5000)) return
        repeat(6) {
            dismissSystemAnr()
            swipeUp()
            if (device.wait(Until.hasObject(uiSelector(text)), 1200)) return
        }
        repeat(6) {
            dismissSystemAnr()
            swipeDown()
            if (device.wait(Until.hasObject(uiSelector(text)), 1200)) return
        }
        val diagnosticDir = File(context.getExternalFilesDir(null), "qa")
        diagnosticDir.mkdirs()
        device.takeScreenshot(File(diagnosticDir, "99-failed-screen.png"))
        runCatching {
            device.dumpWindowHierarchy(File(diagnosticDir, "99-failed-hierarchy.xml"))
        }
        File(diagnosticDir, "99-failed-reason.txt").writeText("Missing text: $text\nPackage: ${device.currentPackageName}\n")
        runCatching {
            File(diagnosticDir, "99-failed-db.txt").writeText(runBlocking {
                database.backupDao().snapshot().toString()
            })
        }.onFailure {
            File(diagnosticDir, "99-failed-db-error.txt").writeText(it.stackTraceToString())
        }
        runCatching {
            File(diagnosticDir, "99-failed-preferences.txt").writeText(runBlocking {
                tripPreferencesRepository(context).preferences.first().toString()
            })
        }.onFailure {
            File(diagnosticDir, "99-failed-preferences-error.txt").writeText(it.stackTraceToString())
        }
        fail("Android 화면에서 다음 문구를 찾지 못했습니다: $text")
    }

    private fun openScreen(button: String, expectedTitle: String) {
        repeat(3) {
            click(button)
            if (device.wait(Until.hasObject(By.text(expectedTitle)), 4500)) return
            Thread.sleep(300)
        }
        awaitText(expectedTitle)
    }

    private fun uiSelector(text: String) =
        if (text.endsWith(" 열기") || text == "저장 장소 관리") By.desc(text) else By.text(text)

    private fun click(text: String) {
        awaitText(text)
        repeat(4) { attempt ->
            try {
                val node: UiObject2 = device.wait(
                    Until.findObject(uiSelector(text)), 3000
                ) ?: throw AssertionError("클릭 대상 없음: $text")
                var clickable = node
                while (!clickable.isClickable) {
                    clickable = clickable.parent ?: break
                }
                if (!clickable.isClickable) {
                    throw AssertionError("클릭 가능한 상위 요소가 없습니다: $text")
                }
                clickable.click()
                device.waitForIdle()
                Thread.sleep(300)
                return
            } catch (stale: StaleObjectException) {
                if (attempt == 3) {
                    throw AssertionError("화면 갱신 후 클릭 대상을 찾지 못했습니다: $text", stale)
                }
                Thread.sleep(200)
            }
        }
    }

    private fun swipeUp() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 25)
    }

    private fun swipeDown() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height / 3, width / 2, height * 3 / 4, 25)
    }

    private fun capture(name: String) {
        device.waitForIdle()
        Thread.sleep(350)
        val dir = File(context.getExternalFilesDir(null), "qa")
        assertTrue("QA screenshot dir creation failed", dir.exists() || dir.mkdirs())
        assertTrue("Screenshot failed: $name", device.takeScreenshot(File(dir, "$name.png")))
    }
}
