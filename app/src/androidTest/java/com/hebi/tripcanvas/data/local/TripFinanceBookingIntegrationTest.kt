package com.hebi.tripcanvas.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.hebi.tripcanvas.data.BookingInput
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.BookingType
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.TripFinanceRepository
import com.hebi.tripcanvas.data.backup.TripBackupCodec
import com.hebi.tripcanvas.domain.ExpenseInput
import com.hebi.tripcanvas.domain.SplitMode
import com.hebi.tripcanvas.domain.calculateSettlement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripFinanceBookingIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun financeAllocationsConfirmAndInvalidateAfterAmountChange() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            db.tripDao().insert(trip("trip"))
            val finance = TripFinanceRepository(db.financeDao(), db.expenseDao())
            val expense = ExpenseRepository(db.expenseDao(), db.tripDao(), db.placeDao())
            val first = finance.addParticipant("trip", "민재")
            val second = finance.addParticipant("trip", "현지")
            val id = expense.save(
                "trip", ExpenseInput(
                    amount = "10", currencyCode = "USD",
                    date = "2026-11-11", time = "09:30"
                )
            )
            finance.saveAllocation(
                "trip", id, first, "1320", "2026-11-10", SplitMode.EQUAL,
                listOf(first to "", second to "")
            )
            val row = requireNotNull(db.expenseDao().find("trip", id))
            assertEquals("1320", row.rateToBaseDecimal)
            assertEquals("사용자 입력", row.rateSource)
            val split = db.financeDao().observeExpenseShares(id).first()
            assertEquals(listOf(6600L, 6600L), split.map { it.amountMinor }.sorted())
            assertTrue(split.all { it.splitMode == "EQUAL" && it.enteredValue.isEmpty() })
            val participants = db.financeDao().getParticipants("trip")
            val summary = calculateSettlement("KRW", participants, listOf(row), split)
            assertTrue(summary.readyToConfirm)
            assertEquals(6600L, summary.transfers.single().amountMinor)
            finance.confirm("trip")
            val confirmed = db.financeDao().observeConfirmation("trip").first()
            assertEquals(summary.fingerprint, confirmed?.fingerprint)

            expense.save(
                "trip", ExpenseInput(
                    amount = "11", currencyCode = "USD",
                    date = "2026-11-11", time = "09:30"
                ), id
            )
            assertTrue(db.financeDao().observeExpenseShares(id).first().isEmpty())
            assertNull(db.expenseDao().find("trip", id)?.rateToBaseDecimal)
            assertNull(db.expenseDao().find("trip", id)?.payerParticipantId)
            val updated = calculateSettlement(
                "KRW", participants, db.expenseDao().observeForTrip("trip").first(),
                db.financeDao().observeTripShares("trip").first()
            )
            assertFalse(updated.readyToConfirm)
            assertFalse(updated.fingerprint == confirmed?.fingerprint)
            assertTrue(runCatching { finance.deleteParticipant("trip", first) }.isSuccess)
        } finally { db.close() }
    }

    @Test
    fun participantReferencesCannotBeDeletedAndBookingMetadataSurvivesBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            db.tripDao().insert(trip("trip"))
            val finance = TripFinanceRepository(db.financeDao(), db.expenseDao())
            val p = finance.addParticipant("trip", "결제자")
            val q = finance.addParticipant("trip", "부담자")
            db.expenseDao().insert(ExpenseEntity(
                "exp", "trip", "100", "KRW", "2026-11-11T09:00:00",
                "FOOD", "", null, 1L, 1L
            ))
            finance.saveAllocation(
                "trip", "exp", p, "", "", SplitMode.EQUAL,
                listOf(p to "", q to "")
            )
            assertTrue(runCatching { finance.deleteParticipant("trip", p) }.isFailure)
            assertTrue(runCatching { finance.deleteParticipant("trip", q) }.isFailure)

            val repository = BookingRepository(db.bookingDao(), db.tripDao())
            val booking = repository.save(
                "trip", BookingInput(
                    type = BookingType.LODGING, title = "숙소",
                    startAtLocal = "2026-11-11T14:30",
                    referenceNumber = "CONFIDENTIAL-123", note = "예약 정보"
                )
            )
            repository.attachDocument(
                "trip", booking, "content://qa/document/file",
                "ticket.pdf", "application/pdf"
            )
            assertEquals(1, repository.attachments(booking).first().size)
            val saved = db.backupDao().snapshot()
            assertEquals(1, saved.bookings.size)
            assertEquals(2, saved.participants.size)
            assertEquals(2, saved.shares.size)
            val decoded = TripBackupCodec.decode(TripBackupCodec.encode(saved))
            assertEquals(saved, decoded)
            assertFalse(TripBackupCodec.encode(decoded).contains("content://qa/document/file"))
            db.backupDao().replaceAll(decoded)
            assertEquals(1, db.bookingDao().observe("trip").first().size)
            assertTrue(db.bookingDao().observeAttachments(booking).first().isEmpty())
        } finally { db.close() }
    }

    private fun trip(id: String) = TripEntity(
        id, "QA 제주 여행", "2026-11-10", "2026-11-12", "한국", "제주",
        "Asia/Seoul", "KRW", "", "", 1L, 1L
    )
}
