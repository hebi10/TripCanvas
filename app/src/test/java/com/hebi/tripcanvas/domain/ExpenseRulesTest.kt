package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ExpenseEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRulesTest {
    private fun input(amount: String = "12.50", date: String = "2026-11-09", time: String = "14:30") =
        ExpenseInput(
            amount = amount, currencyCode = "USD",
            date = date, time = time, category = ExpenseCategory.FOOD
        )

    @Test fun validatesDateTimeAndAmount() {
        val valid = validateExpense(input())
        assertNotNull(valid.valid)
        assertEquals("2026-11-09T14:30:00", valid.valid?.occurredAtLocal)
        assertEquals("FOOD", valid.valid?.category)
        assertTrue(validateExpense(input("0")).errors.containsKey(ExpenseField.Amount))
        assertTrue(validateExpense(input(date = "2026-13-01")).errors.containsKey(ExpenseField.Date))
        assertTrue(validateExpense(input(time = "25:30")).errors.containsKey(ExpenseField.Time))
        assertTrue(validateExpense(input(amount = "12.345")).errors.containsKey(ExpenseField.Amount))
    }

    @Test fun keepsForeignCurrenciesOutOfBaseSummary() {
        val list = listOf(
            expense("one", "0.10", "USD"),
            expense("two", "0.20", "USD"),
            expense("three", "400", "JPY")
        )
        val totals = calculateExpenseTotals(list, "USD")
        assertEquals(BigDecimal("0.30"), totals.baseSpent)
        assertEquals(BigDecimal("400"), totals.unconvertedByCurrency["JPY"])
        assertEquals(3, totals.count)
    }

    private fun expense(id: String, amount: String, currency: String) = ExpenseEntity(
        id = id, tripId = "trip", amountDecimal = amount, currencyCode = currency,
        occurredAtLocal = "2026-11-09T14:30:00",
        category = "FOOD", note = "", placeId = null,
        createdAtMillis = 1L, updatedAtMillis = 1L
    )
}
