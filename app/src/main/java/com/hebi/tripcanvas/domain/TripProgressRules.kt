package com.hebi.tripcanvas.domain

import java.math.BigDecimal
import java.math.RoundingMode

internal fun checklistCompletionFraction(completed: Int, total: Int): Float =
    if (total <= 0) 0f else completed.coerceIn(0, total).toFloat() / total

internal data class BudgetUsageProgress(
    val fraction: Float,
    val percentage: String,
    val overBudget: Boolean
)

internal fun budgetUsageProgress(
    spent: BigDecimal,
    budget: BigDecimal
): BudgetUsageProgress? {
    if (budget.signum() <= 0) return null

    val used = spent.max(BigDecimal.ZERO)
    val fill = when {
        used.signum() == 0 -> 0f
        used >= budget -> 1f
        else -> used.divide(budget, 6, RoundingMode.HALF_UP)
            .toFloat().coerceIn(0f, 1f)
    }
    val percent = used.multiply(BigDecimal("100"))
        .divide(budget, 0, RoundingMode.HALF_UP)
        .toPlainString()

    return BudgetUsageProgress(
        fraction = fill,
        percentage = percent + "%",
        overBudget = used > budget
    )
}
