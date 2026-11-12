package com.hebi.tripcanvas.screens

import java.math.BigDecimal
import java.math.RoundingMode

internal fun checklistCompletionFraction(completed: Int, total: Int): Float {
    if (total <= 0) return 0f
    return completed.coerceIn(0, total).toFloat() / total.toFloat()
}

internal data class BudgetUsageVisual(
    val fraction: Float,
    val percentageLabel: String,
    val overBudget: Boolean
)

internal fun budgetUsageVisual(spent: BigDecimal, amount: BigDecimal?): BudgetUsageVisual? {
    if (amount == null || amount.signum() <= 0) return null
    val nonnegativeSpent = spent.max(BigDecimal.ZERO)
    val cappedFraction = nonnegativeSpent
        .divide(amount, 6, RoundingMode.HALF_UP)
        .min(BigDecimal.ONE)
        .toFloat()
    val percentage = nonnegativeSpent
        .multiply(BigDecimal("100"))
        .divide(amount, 1, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return BudgetUsageVisual(
        fraction = cappedFraction,
        percentageLabel = "$percentage% 사용",
        overBudget = nonnegativeSpent > amount
    )
}
