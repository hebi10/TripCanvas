package com.hebi.tripcanvas.screens

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripProgressPresentationTest {
    @Test fun checklistRatioHandlesEmptyAndInvalidCounts() {
        assertEquals(0f, checklistCompletionFraction(0, 0), 0f)
        assertEquals(0f, checklistCompletionFraction(2, -1), 0f)
        assertEquals(0f, checklistCompletionFraction(-3, 4), 0f)
        assertEquals(1f, checklistCompletionFraction(9, 4), 0f)
    }

    @Test fun checklistRatioUsesAllSavedItemsNotActiveFilter() {
        assertEquals(1f / 3f, checklistCompletionFraction(1, 3), 0.0001f)
        assertEquals(1f, checklistCompletionFraction(3, 3), 0f)
    }

    @Test fun budgetPercentageReflectsOnlyAlreadyConvertedBaseSpend() {
        val result = requireNotNull(budgetUsageVisual(BigDecimal("12500"), BigDecimal("300000")))
        assertEquals("4.2% 사용", result.percentageLabel)
        assertEquals(0.041667f, result.fraction, 0.00001f)
        assertFalse(result.overBudget)
    }

    @Test fun budgetProgressIsUnavailableWithoutComparablePositiveBudget() {
        assertNull(budgetUsageVisual(BigDecimal("500"), null))
        assertNull(budgetUsageVisual(BigDecimal("500"), BigDecimal.ZERO))
        assertNull(budgetUsageVisual(BigDecimal("500"), BigDecimal("-100")))
    }

    @Test fun overspendingKeepsAccurateLabelAndCapsVisualBar() {
        val result = requireNotNull(budgetUsageVisual(BigDecimal("150000"), BigDecimal("100000")))
        assertEquals("150% 사용", result.percentageLabel)
        assertEquals(1f, result.fraction, 0f)
        assertTrue(result.overBudget)
    }

    @Test fun zeroAndNegativeSpendCannotProduceNegativeProgress() {
        val zero = requireNotNull(budgetUsageVisual(BigDecimal.ZERO, BigDecimal("500")))
        assertEquals("0% 사용", zero.percentageLabel)
        assertEquals(0f, zero.fraction, 0f)
        val negative = requireNotNull(budgetUsageVisual(BigDecimal("-5"), BigDecimal("500")))
        assertEquals(0f, negative.fraction, 0f)
    }
}
