package com.hebi.tripcanvas.qa

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.tripcanvas.MainActivity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class TripCanvasReceiptOcrTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val navigator = QaUiNavigator(device)
    private val db = TripDatabase.get(context)
    private val tripId = "qa-ocr"
    private val tripName = "OCR QA trip"
    private val amount = "12000"
    private val date = "2026-11-11"

    @Test
    fun recognizeReceiptAndRequireExplicitExpenseSave() {
        device.executeShellCommand("settings put system font_scale 1.0")
        runBlocking {
            tripPreferencesRepository(context).completeOnboarding()
            tripPreferencesRepository(context).saveThemeMode(TripThemeMode.Light)
            db.backupDao().replaceAll(
                TripBackupSnapshot(
                    trips = listOf(TripEntity(
                        tripId, tripName, date, date, "", "", "Asia/Seoul", "KRW",
                        "", "", 1L, 1L
                    )),
                    places = emptyList(), itineraries = emptyList(), expenses = emptyList(),
                    budgets = emptyList(), checklist = emptyList()
                )
            )
        }
        val uri = createSyntheticReceipt()
        // Supply a test-owned content URI instead of automating a provider-specific picker.
        // The production activity result, ML Kit OCR, parser, dialog and save path remain real.
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                return Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                try {
                    tap(By.text(tripName), tripName, "작업 도구")
                    tap(By.desc("경비 열기"), "경비 열기", "지출 추가")
                    tap(By.text("지출 추가"), "지출 추가", "지출 정보")
                    awaitVisible(By.text("금액 *"), "금액 *")
                    capture("f06-ocr-before-selection")
                    openReceipt()
                    awaitVisible(By.text("영수증 인식 후보 확인"), "OCR 후보 제목", 45_000)
                    awaitVisible(By.text(amount), "인식 금액")
                    awaitVisible(By.text("KRW"), "인식 통화")
                    awaitVisible(By.text(date), "인식 날짜")
                    capture("f07-ocr-candidate-dialog")
                    tap(By.text("취소"), "OCR 취소")
                    assertTrue(device.wait(Until.gone(By.text("영수증 인식 후보 확인")), 5000))
                    assertFalse("OCR 취소 후 입력 금액이 바뀌면 안 됩니다.", device.hasObject(By.text(amount)))
                    assertNoSavedExpense()
                    capture("f08-ocr-cancel-preserved")

                    openReceipt()
                    awaitVisible(By.text("영수증 인식 후보 확인"), "OCR 재인식 후보", 45_000)
                    tap(By.text("입력란에 반영"), "입력란에 반영")
                    assertTrue(device.wait(Until.gone(By.text("영수증 인식 후보 확인")), 5000))
                    awaitVisible(By.text(amount), "반영된 금액")
                    awaitVisible(By.textContains(date), "반영된 날짜")
                    assertNoSavedExpense()
                    capture("f09-ocr-applied-unsaved")
                    tap(By.text("지출 저장"), "지출 저장", "지출 추가")
                    val saved = runBlocking { db.backupDao().snapshot().expenses.single() }
                    assertEquals(tripId, saved.tripId)
                    assertEquals(amount, saved.amountDecimal)
                    assertEquals("KRW", saved.currencyCode)
                    assertTrue(saved.occurredAtLocal.startsWith(date + "T"))
                    assertEquals("두 번의 이미지 선택 결과를 전달해야 합니다.", 2, monitor.hits)
                    awaitVisible(By.textContains("12,000"), "저장된 지출")
                    capture("f10-ocr-expense-saved")
                } catch (failure: Throwable) {
                    runCatching {
                        capture("99-receipt-ocr-failure")
                        device.dumpWindowHierarchy(File(output(), "99-receipt-ocr-hierarchy.xml"))
                    }
                    throw failure
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            context.contentResolver.delete(uri, null, null)
            device.executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun openReceipt() = tap(
        By.text("영수증 사진에서 금액·날짜 읽기"),
        "영수증 사진에서 금액·날짜 읽기"
    )

    private fun assertNoSavedExpense() {
        assertTrue("OCR 후보 확인/반영만으로 지출이 저장되면 안 됩니다.", runBlocking {
            db.backupDao().snapshot().expenses.isEmpty()
        })
    }

    private fun createSyntheticReceipt(): Uri {
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "tripcanvas-qa-receipt-${System.nanoTime()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TripCanvasQA")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ))
        try {
            val bitmap = Bitmap.createBitmap(1400, 800, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 92f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                canvas.drawText("QA RECEIPT", 100f, 165f, paint)
                canvas.drawText(date, 100f, 365f, paint)
                canvas.drawText("TOTAL 12000 KRW", 100f, 565f, paint)
                requireNotNull(resolver.openOutputStream(uri, "w")).use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1)
            return uri
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun tap(selector: BySelector, label: String, expectedAfter: String? = null) {
        awaitVisible(selector, label)
        navigator.clickVisible(selector, label, expectedAfter)
    }

    private fun awaitVisible(selector: BySelector, label: String, timeout: Long = 5000) {
        if (device.wait(Until.hasObject(selector), timeout)) return
        repeat(10) { attempt ->
            val width = device.displayWidth
            val height = device.displayHeight
            val from = if (attempt < 6) height * 3 / 4 else height / 3
            val to = if (attempt < 6) height / 3 else height * 3 / 4
            device.swipe(width / 2, from, width / 2, to, 25)
            if (device.wait(Until.hasObject(selector), 1000)) return
        }
        throw AssertionError("실제 OCR 화면에서 확인할 수 없습니다: $label")
    }

    private fun output(): File = File(context.getExternalFilesDir(null), "qa3b").also {
        assertTrue(it.exists() || it.mkdirs())
    }

    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(400)
        assertTrue(device.takeScreenshot(File(output(), "$name.png")))
    }
}
