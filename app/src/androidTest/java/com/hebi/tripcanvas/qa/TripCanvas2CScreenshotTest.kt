package com.hebi.tripcanvas.qa

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
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
import java.time.LocalDate
import java.time.ZoneId
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
    private val navigator = QaUiNavigator(device)
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
            awaitText("KRW 12,500")
            awaitText("1 / 3")
            capture("01-home-light")
            assertStatusBarBackground(dark = false)
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val emptyFilter = if (today.isAfter(LocalDate.parse("2026-11-22"))) "예정" else "완료"
            val emptyTitle = if (emptyFilter == "예정") "예정된 여행이 없습니다" else "완료된 여행이 없습니다"
            click(emptyFilter)
            awaitText(emptyTitle)
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
            assertTrue(
                "첫 화면에서 일정 빠른 실행이 보여야 합니다.",
                device.wait(Until.hasObject(By.desc("일정 열기")), 3000)
            )
            assertTrue(
                "첫 화면에서 경비 빠른 실행이 보여야 합니다.",
                device.wait(Until.hasObject(By.desc("경비 열기")), 3000)
            )
            capture("02a0-workspace-quick-actions-light")
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
            click("11/20 금")
            awaitText("11월 20일 · 금요일")
            awaitText("11월 20일 · 금요일")
            awaitText("공항 도착")
            awaitText("10:00")
            awaitText("10:30")
            assertTrue(
                "짧은 여행에서 중복 날짜 선택기를 노출하면 안 됩니다.",
                !device.hasObject(By.text("다른 날짜 선택"))
            )
            capture("02d-workspace-schedule-route")
            capture("02d1-schedule-timeline-light")
            click("11/21 토")
            awaitText("11월 21일 · 토요일")
            awaitText("이 날짜에는 일정이 없습니다")
            capture("02f-schedule-empty-date")
            click("11/20 금")
            awaitText("11월 20일 · 금요일")
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
            navigator.clickVisible(By.desc("저장 장소 관리"), "저장 장소 관리", expectedAfter = "여행 장소")
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
            assertTrue(
                "준비물 진행률 접근성 설명이 있어야 합니다.",
                device.wait(Until.hasObject(By.desc("준비 완료 1개 / 전체 3개")), 5000)
            )
            capture("03-checklist-light")
            selectChecklistFilter(
                label = "완료", visible = listOf("여권"), hidden = listOf("충전기", "상비약")
            )
            capture("03a-checklist-completed-filter")
            selectChecklistFilter(
                label = "미완료", visible = listOf("충전기", "상비약"), hidden = listOf("여권")
            )
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
            selectChecklistFilter(
                label = "전체", visible = listOf("여권", "충전기", "상비약"), hidden = emptyList()
            )
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
            awaitText("예산 사용률")
            awaitText("4%")
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
            repeat(12) {
                if (!device.hasObject(By.text("지출 저장"))) {
                    val scrollView = device.findObject(By.scrollable(true))
                        ?: throw AssertionError("지출 입력 화면의 스크롤 영역을 찾지 못했습니다.")
                    scrollView.scroll(Direction.DOWN, 0.75f)
                }
            }
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
            awaitText("암호화 백업 내보내기")
            capture("12-backup-restore-light")
            click("암호화 백업 내보내기")
            awaitText("암호화 백업 비밀번호 설정")
            device.pressBack()
            awaitText("암호화 백업 내보내기")
            awaitText("평문 JSON 내보내기")
            openAndroidDocumentPicker("평문 JSON 내보내기", "save")
            capture("13-android-file-save-picker")
            device.pressBack()
            awaitText("백업·복원")
            awaitText("파일 저장을 취소했습니다. 기존 여행 데이터는 변경되지 않았습니다.")
            assertTrue("백업 저장 취소 후 기존 여행이 유지되어야 합니다.", runBlocking {
                database.tripDao().findById("qa-trip") != null
            })
            capture("14-backup-cancelled")
            openAndroidDocumentPicker("백업 파일 선택 및 검증", "import")
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
            click("일정 열기")
            awaitText("여행 일정")
            click("11/20 금")
            awaitText("11월 20일 · 금요일")
            awaitText("공항 도착")
            capture("16c-schedule-dark")
            device.wait(Until.findObject(By.desc("공항 도착 일정 메뉴")), 5000)
                ?.click() ?: throw AssertionError("다크 모드에서 일정 더보기 메뉴를 찾지 못했습니다.")
            awaitText("복제")
            capture("16d-schedule-actions-menu-dark")
            device.pressBack()
            device.pressBack()
            awaitText("작업 도구")
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
            awaitText("암호화 백업 내보내기")
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
            click("일정 열기")
            awaitText("여행 일정")
            click("11/20 금")
            awaitText("11월 20일 · 금요일")
            capture("21c-schedule-dark-font160-top")
            awaitText("공항 도착")
            capture("21d-schedule-dark-font160-card")
            device.wait(Until.findObject(By.desc("공항 도착 일정 메뉴")), 5000)
                ?.click() ?: throw AssertionError("160% 글꼴에서 일정 더보기 메뉴를 찾지 못했습니다.")
            awaitText("복제")
            capture("21e-schedule-menu-dark-font160")
            device.pressBack()
            device.pressBack()
            awaitText("작업 도구")
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


    @Test
    fun capturePlaceManagementAndSelection() {
        val placeName = "QA Harbor"
        val scheduleName = "QA Harbor visit"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun hideKeyboardIfVisible() {
                var visible = false
                scenario.onActivity { activity ->
                    visible = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                if (visible) device.pressBack()
            }

            fun enterFirstField(value: String) {
                val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                    ?: throw AssertionError("입력 필드를 찾지 못했습니다.")
                field.setText(value)
                hideKeyboardIfVisible()
            }

            fun openPlaceList() {
                awaitText("저장 장소 관리")
                navigator.clickVisible(By.desc("저장 장소 관리"), "저장 장소 관리", "여행 장소")
                awaitText("여행 장소")
            }

            awaitText("제주 주말 여행")
            click("제주 주말 여행")
            openPlaceList()
            awaitText("제주국제공항")
            awaitText("좌표 등록됨")
            awaitText("새 장소 만들기")
            capture("p01-place-list-light")
            navigator.clickVisible(By.text("새 장소 만들기"), "새 장소 만들기", "장소 정보")
            awaitText("장소 정보")
            capture("p02-place-create-light")
            assertFormFieldPair("위도", "경도", expectHorizontal = normalFormUsesColumns(scenario))
            capture("p02a-place-coordinates-light")
            awaitText("장소 정보")
            enterFirstField(placeName)
            awaitText("장소 만들기")
            navigator.clickVisible(By.text("장소 만들기"), "장소 저장", "여행 장소")
            awaitText("여행 장소")
            awaitText("장소 검색")
            enterFirstField(placeName)
            awaitText(placeName)
            awaitText("좌표 없음")
            assertTrue("검색 결과에서 다른 장소가 제외되어야 합니다.", device.wait(Until.gone(By.text("제주국제공항")), 5000))
            val saved = runBlocking { database.backupDao().snapshot().places.single { it.name == placeName } }
            assertEquals(null, saved.latitude)
            assertEquals(null, saved.longitude)
            capture("p03-place-search-light")
            navigator.clickVisible(By.desc("삭제: $placeName"), "장소 삭제", "장소 삭제")
            awaitText("장소 삭제")
            capture("p04-place-delete-confirm-light")
            click("취소")
            awaitText(placeName)
            assertTrue(runBlocking { database.backupDao().snapshot().places.any { it.id == saved.id } })
            navigator.clickVisible(By.desc("상세: $placeName"), "장소 상세", "장소 상세")
            awaitText("좌표 없음 · 지도 핀 미표시")
            capture("p05-place-detail-light")
            navigator.clickVisible(By.text("장소 편집"), "장소 편집", "장소 정보")
            awaitText("장소 정보")
            capture("p06-place-edit-light")
            device.pressBack()
            awaitText("장소 상세")
            device.pressBack()
            awaitText("여행 장소")
            device.pressBack()
            awaitText("작업 도구")
            awaitText("일정 열기")
            navigator.clickVisible(By.desc("일정 열기"), "일정 열기", "일정 추가")
            awaitText("일정 추가")
            navigator.clickVisible(By.text("일정 추가"), "일정 추가", "일정 정보")
            awaitText("일정 정보")
            assertFormFieldPair("시작 시각", "종료 시각", expectHorizontal = normalFormUsesColumns(scenario))
            capture("p06a-schedule-time-fields-light")
            device.pressBack()
            awaitText("여행 일정")
            device.pressBack()
            awaitText("작업 도구")
            device.pressBack()
            awaitText("내 여행")

            runBlocking { tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark) }
            device.executeShellCommand("settings put system font_scale 1.6")
            Thread.sleep(1200)
            scenario.recreate()
            awaitText("내 여행")
            click("제주 주말 여행")
            awaitText("일정 열기")
            navigator.clickVisible(By.desc("일정 열기"), "일정 열기", "일정 추가")
            awaitText("일정 추가")
            navigator.clickVisible(By.text("일정 추가"), "일정 추가", "일정 정보")
            awaitText("일정 정보")
            capture("p07-schedule-form-dark-font160")
            assertFormFieldPair("시작 시각", "종료 시각", expectHorizontal = false)
            capture("p07a-schedule-time-fields-dark-font160")
            awaitText("일정 정보")
            enterFirstField(scheduleName)
            awaitText("장소 선택·추가")
            navigator.clickVisible(By.text("장소 선택·추가"), "장소 선택·추가", "장소 연결")
            awaitText("새 장소 만들기")
            navigator.clickVisible(By.text("새 장소 만들기"), "새 장소 만들기", "장소 정보")
            awaitText("장소 정보")
            assertFormFieldPair("위도", "경도", expectHorizontal = false)
            capture("p07b-place-coordinates-dark-font160")
            device.pressBack()
            awaitText("장소 검색")
            enterFirstField(placeName)
            awaitText(placeName)
            try {
                awaitText("선택: $placeName", By.desc("선택: $placeName"))
                capture("p08-place-select-dark-font160")
                listOf("선택", "상세", "편집", "삭제").forEach { action ->
                    val target = resolvePlaceAction(action, placeName)
                    val bounds = target.visibleBounds
                    assertTrue("장소 버튼은 활성 상태여야 합니다: $action", target.isEnabled)
                    assertTrue("장소 버튼의 화면 내 터치 영역이 필요합니다: $action ($bounds)",
                        bounds.width() > 0 && bounds.height() > 0 &&
                            bounds.left >= 0 && bounds.right <= device.displayWidth)
                }
                val select = resolvePlaceAction("선택", placeName)
                val bounds = select.visibleBounds
                assertTrue("장소 선택 터치 전송 실패", device.click(bounds.centerX(), bounds.centerY()))
                awaitText("장소 연결 해제")
            } catch (failure: AssertionError) {
                val dir = File(context.getExternalFilesDir(null), "qa")
                dir.mkdirs()
                runCatching {
                    capture("99-place-selection-font160-failure")
                    device.dumpWindowHierarchy(File(dir, "99-place-selection-font160-hierarchy.xml"))
                }
                throw failure
            }
            awaitText(placeName)
            capture("p09-schedule-place-linked-dark-font160")
            awaitText("일정 저장")
            navigator.clickVisible(By.text("일정 저장"), "일정 저장", "여행 일정")
            awaitText(scheduleName)
            assertEquals(saved.id, runBlocking {
                database.backupDao().snapshot().itineraries.single { it.title == scheduleName }.placeId
            })
            capture("p10-schedule-saved-dark-font160")
        }
    }

    private fun normalFormUsesColumns(scenario: ActivityScenario<MainActivity>): Boolean {
        var columns = false
        scenario.onActivity { activity ->
            val config = activity.resources.configuration
            val gutter = if (config.screenWidthDp < 600) 32 else 64
            columns = config.screenWidthDp - gutter >= 360 && config.fontScale < 1.3f
        }
        return columns
    }

    private fun assertFormFieldPair(firstLabel: String, secondLabel: String, expectHorizontal: Boolean) {
        awaitText(firstLabel)
        awaitText(secondLabel)
        val first = device.findObject(By.text(firstLabel))
            ?: throw AssertionError("첫 번째 입력 라벨 미노출: $firstLabel")
        val second = device.findObject(By.text(secondLabel))
            ?: throw AssertionError("두 번째 입력 라벨 미노출: $secondLabel")
        val a = first.visibleBounds
        val b = second.visibleBounds
        assertTrue("입력 라벨의 화면 내 표시 영역이 필요합니다: $a, $b",
            a.width() > 0 && a.height() > 0 && b.width() > 0 && b.height() > 0 &&
                a.left >= 0 && b.right <= device.displayWidth)
        val tolerance = (context.resources.displayMetrics.density * 20).toInt()
        if (expectHorizontal) {
            assertTrue("일반 글꼴 입력란은 가로로 배치되어야 합니다: $a, $b",
                b.left > a.right && kotlin.math.abs(a.centerY() - b.centerY()) <= tolerance)
        } else {
            assertTrue("큰 글꼴 또는 좁은 화면 입력란은 세로로 배치되어야 합니다: $a, $b",
                b.top > a.bottom && kotlin.math.abs(a.left - b.left) <= tolerance)
        }
    }

    private fun resolvePlaceAction(action: String, placeName: String): UiObject2 {
        val description = "$action: $placeName"
        awaitText(description, By.desc(description))
        repeat(4) {
            try {
                val node = device.wait(Until.findObject(By.desc(description)), 3000)
                    ?: throw AssertionError("장소 버튼 설명이 없습니다: $description")
                if (node.isClickable) return node

                // A Compose semantics modifier can expose a description on a
                // wrapper rather than on the native clickable Button node.
                val inner = node.findObjects(By.clickable(true)).filter { child ->
                    child.text == action || child.findObject(By.text(action)) != null
                }
                if (inner.size == 1) return inner.single()

                var parent = node.parent
                repeat(8) {
                    val candidate = parent ?: return@repeat
                    if (candidate.isClickable &&
                        (candidate.contentDescription == description ||
                            candidate.text == action ||
                            candidate.findObject(By.text(action)) != null)) {
                        return candidate
                    }
                    parent = candidate.parent
                }
                throw AssertionError("설명에 대응하는 실제 클릭 가능한 버튼을 찾지 못했습니다: $description")
            } catch (_: StaleObjectException) {
                Thread.sleep(200)
            }
        }
        throw AssertionError("화면 갱신 후 장소 버튼을 다시 찾지 못했습니다: $description")
    }

    @Test
    fun captureLiveMemoriesAndReportFromRealAndroidUi() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
        val visitNote = "QA visit note"
        val caption = "QA linked travel memory"
        runBlocking {
            val snapshot = database.backupDao().snapshot()
            val trip = snapshot.trips.single().copy(startDate = today, endDate = today)
            val schedule = snapshot.itineraries.single().copy(date = today)
            database.backupDao().replaceAll(snapshot.copy(
                trips = listOf(trip), itineraries = listOf(schedule)
            ))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun enterVisibleText(label: String, value: String) {
                awaitText(label)
                val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                    ?: throw AssertionError("입력란을 찾지 못했습니다: $label")
                field.setText(value)
                var imeVisible = false
                scenario.onActivity { activity ->
                    imeVisible = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                if (imeVisible) device.pressBack()
            }
            awaitText("제주 주말 여행")
            click("제주 주말 여행")
            awaitText("작업 도구")
            navigator.clickVisible(By.desc("오늘의 여행 열기"), "오늘의 여행 열기", "오늘의 일정")
            awaitText(today)
            awaitText("Day 1 / 1")
            awaitText("방문 완료 0 / 1")
            awaitText("오늘 예정된 예약이 없습니다.")
            capture("24-p3-live-before-checkin")
            awaitText("방문 메모 (선택)")
            enterVisibleText("방문 메모 (선택)", visitNote)
            awaitText("공항 도착")
            capture("j01-live-note-light")
            click("방문 완료")
            awaitText("방문 완료 1 / 1")
            val visit = runBlocking { database.backupDao().snapshot().visits.single() }
            assertEquals("qa-schedule", visit.itineraryItemId)
            assertEquals(visitNote, visit.note)
            assertTrue("완료된 방문을 다시 입력하는 폼이 나타나면 안 됩니다.",
                !device.hasObject(By.text("방문 메모 (선택)")))
            capture("25-p3-live-after-checkin")
            navigator.clickVisible(By.text("빠른 지출 등록"), "빠른 지출 등록", "지출 정보")
            awaitText("금액 *")
            capture("j02-live-expense-action")
            device.pressBack()
            awaitText("방문 완료 1 / 1")

            navigator.clickVisible(By.text("방문·사진 기록"), "방문·사진 기록", "새 기록")
            awaitText("방문 기록")
            awaitText("공항 도착 방문 기록", By.textStartsWith("공항 도착 · "))
            awaitText(visitNote)
            capture("26-p3-memories-visit")
            navigator.clickVisible(By.text("이 방문에 사진 연결"), "이 방문에 사진 연결", "선택한 방문에 연결")
            awaitText("선택한 방문에 연결")
            awaitText("사진 설명·메모")
            capture("j03-memory-editor-linked")
            enterVisibleText("사진 설명·메모", caption)
            navigator.clickVisible(By.text("기록 저장"), "기록 저장", "1건의 사진·메모")
            awaitText("1건의 사진·메모")
            awaitText(caption)
            val moment = runBlocking { database.backupDao().snapshot().moments.single() }
            assertEquals(visit.id, moment.visitId)
            assertEquals(caption, moment.caption)
            assertEquals(null, moment.photoUri)
            capture("j04-memory-saved-light")
            awaitText("사진 원본 ZIP 저장")
            val exportButton = device.findObject(By.text("사진 원본 ZIP 저장"))
                ?: throw AssertionError("사진 내보내기 버튼 미노출")
            var exportAction = exportButton
            while (!exportAction.isClickable && exportAction.parent != null) {
                exportAction = requireNotNull(exportAction.parent)
            }
            assertTrue("사진 없는 메모만 저장된 경우 사진 ZIP 내보내기는 비활성이어야 합니다.",
                !exportAction.isEnabled)
            capture("j05-memory-no-photo-export")
            device.pressBack()
            awaitText("방문 완료 1 / 1")
            assertEquals(1, runBlocking { database.backupDao().snapshot().visits.size })
            device.pressBack()
            awaitText("작업 도구")
            navigator.clickVisible(By.desc("리포트 열기"), "리포트 열기", "여행 요약")
            awaitText("여행 기간: 1일")
            awaitText("실제 방문: 1건")
            awaitText("사진·메모 기록: 1건")
            capture("27-p3-report-visited")
            awaitText("등록된 기록 사진이 없습니다.")
            awaitText("기록된 경비: KRW 12,500")
            awaitText("미환산 외화 지출은 합계에서 제외했습니다.")
            capture("j06-report-expense-light")

            runBlocking { tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark) }
            device.executeShellCommand("settings put system font_scale 1.6")
            Thread.sleep(1200)
            scenario.recreate()
            awaitText("여행 리포트")
            awaitText("여행 기간: 1일")
            awaitText("사진·메모 기록: 1건")
            capture("j07-report-font160")
            awaitText("기록된 경비: KRW 12,500")
            capture("j08-report-expense-font160")
        }
    }

    @Test
    fun captureSettingsAndBackupSafety() {
        val before = runBlocking { database.backupDao().snapshot() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun hideKeyboard() {
                var visible = false
                scenario.onActivity { activity ->
                    visible = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                if (visible) device.pressBack()
            }
            awaitText("내 여행")
            click("설정")
            awaitText("화면 모드")
            capture("s01-settings-light")
            awaitText("암호화 백업(.tcb)")
            awaitText("첨부 포함 암호화 백업(.tcm)")
            awaitText("평문 JSON")
            capture("s02-settings-backup-formats")
            awaitText("광고 제거")
            capture("s03-settings-subscription")
            awaitText("TripCanvas · " + com.hebi.tripcanvas.BuildConfig.VERSION_NAME +
                " (" + com.hebi.tripcanvas.BuildConfig.VERSION_CODE + ")")
            capture("s04-settings-version")
            navigator.clickVisible(By.text("로컬 백업·복원"), "로컬 백업·복원", "로컬 데이터 백업")
            awaitText("암호화 백업 내보내기")
            capture("s05-backup-overview")
            click("암호화 백업 내보내기")
            awaitText("암호화 백업 비밀번호 설정")
            capture("s06-backup-password-empty")
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals(2, fields.size)
            fields[0].setText("qa".repeat(3))
            hideKeyboard()
            awaitText("12자 이상 입력해 주세요.")
            capture("s07-backup-password-short")
            val passwordFields = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals(2, passwordFields.size)
            passwordFields[0].setText("qa".repeat(8))
            passwordFields[1].setText("qb".repeat(8))
            hideKeyboard()
            awaitText("비밀번호가 일치하지 않습니다.")
            capture("s08-backup-password-mismatch")
            click("취소")
            awaitText("첨부 포함 암호화 백업 내보내기")
            capture("s09-backup-media-format")
            awaitText("평문 JSON 내보내기")
            capture("s10-backup-plain-format")
            awaitText("첨부 포함 백업 선택 및 검증")
            capture("s11-backup-restore-options")
            awaitText("복원 전 안전 백업")
            capture("s12-backup-checkpoint")
            navigator.clickVisible(By.text("사용하지 않는 복원 파일 정리"), "사용하지 않는 복원 파일 정리", "복원 파일 정리")
            awaitText("복원 파일 정리")
            capture("s13-backup-cleanup-confirm")
            click("취소")
            assertEquals(before, runBlocking { database.backupDao().snapshot() })
            device.pressBack()
            awaitText("설정")
            runBlocking { tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Dark) }
            device.executeShellCommand("settings put system font_scale 1.6")
            Thread.sleep(1200)
            scenario.recreate()
            awaitText("화면 모드")
            capture("s14-settings-dark-font160")
            navigator.clickVisible(By.text("로컬 백업·복원"), "로컬 백업·복원", "로컬 데이터 백업")
            awaitText("암호화 백업 내보내기")
            capture("s15-backup-dark-font160")
            awaitText("복원 전 안전 백업")
            awaitText("사용하지 않는 복원 파일 정리")
            capture("s16-checkpoint-dark-font160")
            assertEquals(before, runBlocking { database.backupDao().snapshot() })
        }
    }

    @Test
    fun captureVerifiedAttachmentRestoreCancellation() {
        // Only synthetic test data is included in this separate portable archive.
        val original = runBlocking { database.backupDao().snapshot() }
        val resolver = context.contentResolver
        val pass = ("qa" + "restore".repeat(3)).toCharArray()
        val archiveDb = androidx.room.Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        val created = mutableListOf<android.net.Uri>()
        fun newDocument(mime: String, suffix: String): android.net.Uri {
            val uri = requireNotNull(resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        "tripcanvas-qa-restore-${System.nanoTime()}.$suffix")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/TripCanvasQA")
                }
            ))
            created.add(uri)
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.flush() }
            return uri
        }
        var monitor: android.app.Instrumentation.ActivityMonitor? = null
        try {
            val image = newDocument("image/png", "png")
            val archive = newDocument("application/octet-stream", "tcm")
            val bitmap = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.DKGRAY)
                requireNotNull(resolver.openOutputStream(image, "wt")).use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
            runBlocking {
                archiveDb.tripDao().insert(
                    original.trips.single().copy(id = "qa-portable-preview", title = "QA portable preview")
                )
                archiveDb.travelJournalDao().insertMoment(
                    com.hebi.tripcanvas.data.local.TravelMomentEntity(
                        "qa-portable-photo", "qa-portable-preview", null,
                        image.toString(), "QA portable photo", 1000L
                    )
                )
                assertEquals(1, com.hebi.tripcanvas.data.backup.PortableAttachmentBackupService(
                    context, archiveDb
                ).export(archive, pass).files)
            }
            val pickerMonitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                    if (intent.action != android.content.Intent.ACTION_OPEN_DOCUMENT) return null
                    return android.app.Instrumentation.ActivityResult(
                        android.app.Activity.RESULT_OK,
                        android.content.Intent().setData(archive)
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            }
            monitor = pickerMonitor
            instrumentation.addMonitor(pickerMonitor)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText("내 여행")
                click("설정")
                navigator.clickVisible(By.text("로컬 백업·복원"), "로컬 백업·복원", "로컬 데이터 백업")
                awaitText("첨부 포함 백업 선택 및 검증")
                click("첨부 포함 백업 선택 및 검증")
                awaitText("암호화 백업 비밀번호 입력")
                capture("s17-backup-password-import")
                val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                    ?: throw AssertionError("백업 비밀번호 입력란 미노출")
                field.setText(String(pass))
                var visible = false
                scenario.onActivity { activity ->
                    visible = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                if (visible) device.pressBack()
                navigator.clickVisible(By.text("비밀번호 확인 및 검증"), "첨부 백업 검증")
                awaitText("첨부 파일 포함 전체 복원")
                awaitText("첨부 원본 수", By.textContains("사진·예약 원본 1개"))
                capture("s18-backup-attachment-verified")
                assertEquals(original, runBlocking { database.backupDao().snapshot() })
                click("취소")
                awaitText("복원을 취소했습니다. 기존 여행 데이터는 유지됩니다.")
                assertEquals(original, runBlocking { database.backupDao().snapshot() })
                assertEquals(1, pickerMonitor.hits)
                capture("s19-backup-attachment-cancelled")
            }
        } finally {
            monitor?.let { instrumentation.removeMonitor(it) }
            created.forEach { resolver.delete(it, null, null) }
            pass.fill('\u0000')
            archiveDb.close()
        }
    }

    @Test
    fun captureJournalPhotoAndZipExport() {
        val resolver = context.contentResolver
        val created = mutableListOf<android.net.Uri>()
        fun newDocument(mime: String, suffix: String): android.net.Uri {
            val uri = requireNotNull(resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        "tripcanvas-qa-journal-${System.nanoTime()}.$suffix")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/TripCanvasQA")
                }
            ))
            created.add(uri)
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.flush() }
            return uri
        }
        var monitor: android.app.Instrumentation.ActivityMonitor? = null
        try {
            val image = newDocument("image/png", "png")
            val output = newDocument("application/zip", "zip")
            val bitmap = android.graphics.Bitmap.createBitmap(640, 480, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.rgb(80, 110, 140))
                val paint = android.graphics.Paint().apply { color = Color.WHITE; textSize = 48f }
                canvas.drawText("QA PHOTO", 80f, 240f, paint)
                requireNotNull(resolver.openOutputStream(image, "wt")).use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
            val originalBytes = requireNotNull(resolver.openInputStream(image)).use { it.readBytes() }
            runBlocking {
                database.travelJournalDao().insertMoment(
                    com.hebi.tripcanvas.data.local.TravelMomentEntity(
                        "qa-journal-photo", "qa-trip", null, image.toString(),
                        "QA photo record", 1000L
                    )
                )
            }
            val original = runBlocking { database.travelJournalDao().observeMoments("qa-trip").first() }
            val pickerMonitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                    if (intent.action != android.content.Intent.ACTION_CREATE_DOCUMENT) return null
                    return android.app.Instrumentation.ActivityResult(
                        android.app.Activity.RESULT_OK,
                        android.content.Intent().setData(output)
                            .addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    )
                }
            }
            monitor = pickerMonitor
            instrumentation.addMonitor(pickerMonitor)
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("제주 주말 여행")
                click("제주 주말 여행")
                awaitText("여행 기록 열기")
                navigator.clickVisible(By.desc("여행 기록 열기"), "여행 기록 열기", "새 기록")
                awaitText("QA photo record")
                awaitText("여행 기록 사진", By.desc("여행 기록 사진"))
                capture("s20-journal-photo-preview")
                awaitText("사진 원본 ZIP 저장")
                navigator.clickVisible(By.text("사진 원본 ZIP 저장"), "사진 원본 ZIP 저장")
                awaitText("사진 원본 1개 ZIP 내보내기 완료")
                capture("s21-journal-zip-exported")
                val entries = linkedMapOf<String, ByteArray>()
                java.util.zip.ZipInputStream(requireNotNull(resolver.openInputStream(output))).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries[entry.name] = zip.readBytes()
                        zip.closeEntry()
                    }
                }
                assertEquals(setOf("photos/000.png", "manifest.json"), entries.keys)
                assertTrue(originalBytes.contentEquals(requireNotNull(entries["photos/000.png"])))
                assertTrue(originalBytes.contentEquals(requireNotNull(resolver.openInputStream(image)).use { it.readBytes() }))
                assertEquals(original, runBlocking { database.travelJournalDao().observeMoments("qa-trip").first() })
                assertEquals(1, pickerMonitor.hits)
                device.pressBack()
                awaitText("작업 도구")
                navigator.clickVisible(By.desc("리포트 열기"), "리포트 열기", "여행 요약")
                awaitText("대표 기록 사진")
                awaitText("여행 기록 사진", By.desc("여행 기록 사진"))
                capture("s22-report-photo-preview")
            }
        } finally {
            monitor?.let { instrumentation.removeMonitor(it) }
            created.forEach { resolver.delete(it, null, null) }
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

    private fun selectChecklistFilter(label: String, visible: List<String>, hidden: List<String>) {
        // Compose FilterChip exposes checked state on the clickable parent, not selected on its text.
        val selector = By.text(label)
        val selected = By.checkable(true).checked(true).clickable(true).hasChild(By.text(label))
        try {
            awaitText(label, selector)
            navigator.clickVisible(selector, "준비물 $label 필터")
            assertTrue(
                "준비물 $label 필터의 선택 상태가 반영되어야 합니다.",
                device.wait(Until.hasObject(selected), 5000)
            )
            visible.forEach { title ->
                awaitText(title, By.desc("$title 준비 여부"))
            }
            hidden.forEach { title ->
                assertTrue(
                    "준비물 $label 필터에서 $title 항목이 사라져야 합니다.",
                    device.wait(Until.gone(By.desc("$title 준비 여부")), 5000)
                )
                assertTrue(
                    "준비물 $label 필터에서 $title 문구가 보이지 않아야 합니다.",
                    device.wait(Until.gone(By.text(title)), 5000)
                )
            }
        } catch (error: AssertionError) {
            val dir = File(context.getExternalFilesDir(null), "qa")
            dir.mkdirs()
            runCatching {
                device.takeScreenshot(File(dir, "99-checklist-filter-failure.png"))
                device.dumpWindowHierarchy(File(dir, "99-checklist-filter-hierarchy.xml"))
            }
            throw error
        }
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

    private fun awaitText(text: String, selector: BySelector = uiSelector(text)) {
        dismissSystemAnr()
        if (device.wait(Until.hasObject(selector), 5000)) return
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(12) {
                dismissSystemAnr()
                // Scroll the actual Compose viewport rather than crossing a nested clickable card.
                val scroll = device.findObject(By.scrollable(true))
                val moved = if (scroll != null) {
                    try {
                        scroll.scroll(direction, 0.75f)
                    } catch (_: StaleObjectException) {
                        false
                    }
                } else false
                if (!moved) {
                    if (direction == Direction.DOWN) swipeUp() else swipeDown()
                }
                device.waitForIdle()
                if (device.wait(Until.hasObject(selector), 1200)) return
            }
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

    private fun openAndroidDocumentPicker(label: String, kind: String) {
        val documentPackages = setOf("com.android.documentsui", "com.google.android.documentsui")
        awaitText(label)
        repeat(2) { attempt ->
            if (device.currentPackageName in documentPackages) return
            assertEquals("Document picker must be launched from TripCanvas", context.packageName, device.currentPackageName)
            navigator.clickVisible(By.text(label), label)
            repeat(30) {
                if (device.currentPackageName in documentPackages) return
                Thread.sleep(300)
            }
            if (attempt == 0 && device.currentPackageName == context.packageName) {
                // Retry only when the action has not left the app.
                awaitText(label)
            } else {
                capture("99-document-picker-$kind-failure")
                val dir = File(context.getExternalFilesDir(null), "qa")
                runCatching { device.dumpWindowHierarchy(File(dir, "99-document-picker-$kind-hierarchy.xml")) }
                fail("Android $kind document picker not confirmed; foreground=" + device.currentPackageName)
            }
        }
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
