package com.financialcoach.analytics;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;

/** Spending of one category group in a month against its budget. Amounts are positive. */
public record CategoryBudgetUsage(CategoryGroup categoryGroup, BigDecimal budget, BigDecimal spent,
                                  BudgetStatus status) {

    /** Negative when the budget was exceeded. */
    public BigDecimal remaining() {
        return budget.subtract(spent);
    }

    /** 2 decimals; null for a zero budget (any spending exceeds it). */
    public BigDecimal percentageUsed() {
        return budget.signum() == 0 ? null : Percentages.of(spent, budget);
    }
}
