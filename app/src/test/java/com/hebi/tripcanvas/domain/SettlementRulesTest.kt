package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ExpenseShareEntity
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementRulesTest {
    private val first = TripParticipantEntity("p1", "trip", "민재", 1L, 1L)
    private val second = TripParticipantEntity("p2", "trip", "현지", 2L, 2L)

    private fun expense(
        currency: String = "KRW",
        amount: String = "101",
        payer: String? = "p1",
        rate: String? = null
    ) = ExpenseEntity(
        id = "exp", tripId = "trip", amountDecimal = amount, currencyCode = currency,
        occurredAtLocal = "2026-11-11T09:30:00", category = "FOOD",
        note = "", placeId = null, createdAtMillis = 1L, updatedAtMillis = 1L,
        payerParticipantId = payer,
        baseCurrencyCode = if (rate == null && currency != "KRW") null else "KRW",
        rateToBaseDecimal = rate,
        rateDate = if (rate == null) null else "2026-11-10",
        rateSource = if (rate == null) null else "사용자 입력"
    )

    @Test fun equalSplitDistributesRemainderDeterministically() {
        val rows = allocateShares(
            101L, SplitMode.EQUAL,
            listOf("p1" to "", "p2" to "", "p3" to ""), "KRW"
        )
        assertEquals(listOf(34L, 34L, 33L), rows.map { it.amountMinor })
        assertEquals(101L, rows.sumOf { it.amountMinor })
    }

    @Test fun percentAndAmountSharesRequireExactTotals() {
        val percent = allocateShares(
            101L, SplitMode.PERCENT,
            listOf("p1" to "60", "p2" to "40"), "KRW"
        )
        assertEquals(listOf(61L, 40L), percent.map { it.amountMinor })
        val fixed = allocateShares(
            101L, SplitMode.AMOUNT,
            listOf("p1" to "50", "p2" to "51"), "KRW"
        )
        assertEquals(listOf(50L, 51L), fixed.map { it.amountMinor })
        assertTrue(runCatching {
            allocateShares(101L, SplitMode.AMOUNT, listOf("p1" to "50", "p2" to "50"), "KRW")
        }.isFailure)
        assertTrue(runCatching {
            allocateShares(101L, SplitMode.PERCENT, listOf("p1" to "30", "p2" to "60"), "KRW")
        }.isFailure)
    }

    @Test fun foreignCurrencyRequiresAnExplicitSnapshot() {
        val original = expense(currency = "USD", amount = "12.50", payer = null)
        assertEquals(null, convertedMinor(original, "KRW", null))
        val converted = expense(currency = "USD", amount = "12.50", rate = "1320")
        assertEquals(16500L, convertedMinor(converted, "KRW", "1320"))
        assertEquals("1320", validateExchangeRate("1320.0000", "2026-11-10"))
        assertTrue(runCatching { validateExchangeRate("0", "2026-11-10") }.isFailure)
        assertTrue(runCatching { validateExchangeRate("1.5", "invalid") }.isFailure)
    }

    @Test fun settlementTracksPayerAndNetTransferWithoutSendingFunds() {
        val cost = expense()
        val shares = listOf(
            ExpenseShareEntity("exp", "p1", 51, "KRW"),
            ExpenseShareEntity("exp", "p2", 50, "KRW")
        )
        val report = calculateSettlement("KRW", listOf(first, second), listOf(cost), shares)
        assertTrue(report.readyToConfirm)
        assertEquals(0, report.unconverted)
        assertEquals(0, report.unallocated)
        assertEquals(1, report.includedExpenses)
        assertEquals(listOf("p2" to "p1"), report.transfers.map {
            it.fromParticipantId to it.toParticipantId
        })
        assertEquals(50L, report.transfers.single().amountMinor)
        assertEquals(50L, report.balances.first().netMinor)
        assertEquals(-50L, report.balances.last().netMinor)
        assertFalse(report.fingerprint == settlementFingerprint(
            "KRW", listOf(first, second), listOf(cost.copy(updatedAtMillis = 2L)), shares
        ))
        assertFalse(report.fingerprint == settlementFingerprint(
            "KRW", listOf(first, second), listOf(cost),
            shares.map { it.copy(splitMode = "EQUAL") }
        ))
        assertFalse(report.fingerprint == settlementFingerprint(
            "KRW", listOf(first, second), listOf(cost),
            shares.map { it.copy(splitMode = "PERCENT", enteredValue = "50") }
        ))
    }

    @Test fun missingRateOrParticipantsNeverCountAsCompletedSettlement() {
        val rateMissing = expense(currency = "USD", amount = "10", payer = null)
        val report = calculateSettlement(
            "KRW", listOf(first, second), listOf(rateMissing), emptyList()
        )
        assertFalse(report.readyToConfirm)
        assertEquals(1, report.unconverted)
        val another = calculateSettlement("KRW", listOf(first, second), listOf(expense()), emptyList())
        assertFalse(another.readyToConfirm)
        assertEquals(1, another.unallocated)
    }
}
