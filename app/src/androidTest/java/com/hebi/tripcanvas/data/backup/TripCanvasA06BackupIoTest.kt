package com.hebi.tripcanvas.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.local.BookingEntity
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ExpenseShareEntity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import com.hebi.tripcanvas.data.local.TripSettlementEntity
import com.hebi.tripcanvas.domain.calculateSettlement
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripCanvasA06BackupIoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun contentUriExportPreviewRestoreKeepsFinancialRowsAndExcludesAttachmentUris() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        val uri = createTestDocument()
        try {
            val expected = completeSnapshot()
            db.backupDao().replaceAll(expected)

            val booking = BookingRepository(db.bookingDao(), db.tripDao())
            booking.attachDocument(
                "a06-trip", "a06-booking",
                "content://qa-a06.invalid/documents/private.pdf",
                "test-document.pdf", "application/pdf"
            )
            assertEquals(1, booking.attachments("a06-booking").first().size)

            val service = TripBackupService(context, db)
            assertEquals(expected, service.export(uri))
            val exportedText = context.contentResolver.openInputStream(uri)!!.use {
                it.reader(StandardCharsets.UTF_8).readText()
            }
            assertTrue(exportedText.contains("QA-REFERENCE-EXAMPLE"))
            assertFalse(exportedText.contains("content://qa-a06.invalid"))
            assertFalse(exportedText.contains("test-document.pdf"))
            assertFalse(exportedText.contains("uriString"))

            val preview = service.preview(uri)
            assertEquals(expected, preview)
            assertEquals(expected.settlements.single().fingerprint,
                preview.settlements.single().fingerprint)

            db.tripDao().insert(trip("a06-unrelated"))
            assertEquals(2, db.backupDao().allTrips().size)
            service.restore(preview)

            assertEquals(expected, db.backupDao().snapshot())
            assertEquals(1, booking.observe("a06-trip").first().size)
            assertTrue(booking.attachments("a06-booking").first().isEmpty())
            assertEquals(1, db.backupDao().allTrips().size)
        } finally {
            context.contentResolver.delete(uri, null, null)
            db.close()
        }
    }

    @Test
    fun corruptedContentUriPreviewDoesNotAlterExistingRoomData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        val uri = createTestDocument()
        try {
            val existing = TripBackupSnapshot(
                trips = listOf(trip("a06-existing")),
                places = emptyList(),
                itineraries = emptyList(),
                expenses = emptyList(),
                budgets = emptyList(),
                checklist = emptyList()
            )
            db.backupDao().replaceAll(existing)
            context.contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write("{\"format\":\"wrong\",\"version\":99}".toByteArray(StandardCharsets.UTF_8))
            }

            val service = TripBackupService(context, db)
            assertTrue(runCatching { service.preview(uri) }.isFailure)
            assertEquals(existing, db.backupDao().snapshot())
        } finally {
            context.contentResolver.delete(uri, null, null)
            db.close()
        }
    }

    private fun createTestDocument(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "tripcanvas-qa-a06-${System.nanoTime()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/TripCanvasQA"
            )
        }
        return requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Android MediaStore could not create a temporary QA document" }
    }

    private fun completeSnapshot(): TripBackupSnapshot {
        val people = listOf(
            TripParticipantEntity("a06-payer", "a06-trip", "결제자", 1L, 1L),
            TripParticipantEntity("a06-traveler", "a06-trip", "동행자", 1L, 1L)
        )
        val expense = ExpenseEntity(
            "a06-expense", "a06-trip", "12.50", "USD", "2026-11-11T09:30:00",
            "FOOD", "테스트 식사", "a06-place", 1L, 1L,
            payerParticipantId = "a06-payer", baseCurrencyCode = "KRW",
            rateToBaseDecimal = "1320", rateDate = "2026-11-10",
            rateSource = "사용자 입력"
        )
        val shares = listOf(
            ExpenseShareEntity("a06-expense", "a06-payer", 8250L, "KRW", "EQUAL"),
            ExpenseShareEntity("a06-expense", "a06-traveler", 8250L, "KRW", "EQUAL")
        )
        val fingerprint = calculateSettlement(
            "KRW", people, listOf(expense), shares
        ).fingerprint
        return TripBackupSnapshot(
            trips = listOf(trip("a06-trip")),
            places = listOf(PlaceEntity(
                "a06-place", "a06-trip", "서울역", "서울시", "",
                37.555, 126.97, 1L, 1L
            )),
            itineraries = listOf(ItineraryItemEntity(
                "a06-plan", "a06-trip", "2026-11-11", "기차",
                "09:00", "10:00", "a06-place", "",
                false, true, 0, 1L, 1L
            )),
            expenses = listOf(expense),
            budgets = listOf(TripBudgetEntity("a06-trip", "100000", "KRW", 1L)),
            checklist = listOf(ChecklistItemEntity(
                "a06-item", "a06-trip", "여권", true, 0, 1L, 1L
            )),
            participants = people,
            shares = shares,
            settlements = listOf(TripSettlementEntity("a06-trip", fingerprint, 1000L)),
            bookings = listOf(BookingEntity(
                "a06-booking", "a06-trip", "LODGING", "테스트 숙소",
                "2026-11-11T14:00", "QA-REFERENCE-EXAMPLE", "", 1L, 1L
            ))
        )
    }

    private fun trip(id: String) = TripEntity(
        id, "QA 테스트 여행", "2026-11-10", "2026-11-12",
        "한국", "서울", "Asia/Seoul", "KRW",
        "", "", 1L, 1L
    )
}
