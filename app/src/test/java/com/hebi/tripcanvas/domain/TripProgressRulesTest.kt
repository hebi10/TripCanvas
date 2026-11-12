package com.hebi.tripcanvas.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripProgressRulesTest {
    @Test fun checklistEmptyAndCompletedCases() {
        assertEquals(0f, checklistCompletionFraction(0, 0), 0f)
        assertEquals(0f, checklistCompletionFraction(0, 3), 0f)
        assertEquals(1f / 3f, checklistCompletionFraction(1, 3), 0.0001f)
        assertEquals(1f, checklistCompletionFraction(3, 3), 0f)
    }

    @Test fun checklistInvalidCountsCannotOverflowBar() {
        assertEquals(1f, checklistCompletionFraction(5, 3), 0f)
        assertEquals(0f, checklistCompletionFraction(-1, 3), 0f)
        assertEquals(0f, checklistCompletionFraction(2, -1), 0f)
    }

    @Test fun budgetProgressUsesExistingConvertedBaseSpend() {
        val result = requireNotNull(
            budgetUsageProgress(BigDecimal("12500"), BigDecimal("300000"))
        )
        assertEquals(0.041667f, result.fraction, 0.000002f)
        assertEquals("4%", result.percentage)
        assertFalse(result.overBudget)
    }

    @Test fun zeroUsedAndExactBudgetCases() {
        val zero = requireNotNull(
            budgetUsageProgress(BigDecimal.ZERO, BigDecimal("100"))
        )
        assertEquals("0%", zero.percentage)
        assertEquals(0f, zero.fraction, 0f)

        val full = requireNotNull(
            budgetUsageProgress(BigDecimal("100"), BigDecimal("100"))
        )
        assertEquals("100%", full.percentage)
        assertEquals(1f, full.fraction, 0f)
        assertFalse(full.overBudget)
    }

    @Test fun overspentValueRemainsAccurateWhileBarIsBounded() {
        val progress = requireNotNull(
            budgetUsageProgress(BigDecimal("120"), BigDecimal("100"))
        )
        assertEquals("120%", progress.percentage)
        assertEquals(1f, progress.fraction, 0f)
        assertTrue(progress.overBudget)
    }

    @Test fun invalidBudgetDoesNotProduceMisleadingBar() {
        assertNull(budgetUsageProgress(BigDecimal.ONE, BigDecimal.ZERO))
        assertNull(budgetUsageProgress(BigDecimal.ONE, BigDecimal("-1")))
        val negative = requireNotNull(
            budgetUsageProgress(BigDecimal("-5"), BigDecimal("100"))
        )
        assertEquals("0%", negative.percentage)
        assertEquals(0f, negative.fraction, 0f)
    }
}
