package com.financialcoach.analytics;

import java.math.BigDecimal;

/** {@code BudgetStatus} of openapi.yaml: of the account's budget and of each category budget. */
public enum BudgetStatus {
    /** Below 80 % of the budget. */
    ON_TRACK,
    /** From 80 % to 100 % (inclusive). */
    WARNING,
    /** Above 100 %. */
    EXCEEDED,
    /** The account has no monthly budget. */
    NO_BUDGET;

    private static final BigDecimal WARNING_FROM = BigDecimal.valueOf(80);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Compared on exact amounts, not on the rounded percentage: 79.999 % is still ON_TRACK.
     * A zero budget is EXCEEDED as soon as anything is spent.
     */
    static BudgetStatus of(BigDecimal spent, BigDecimal budget) {
        BigDecimal spentTimes100 = spent.multiply(HUNDRED);
        if (spentTimes100.compareTo(budget.multiply(HUNDRED)) > 0) {
            return EXCEEDED;
        }
        if (budget.signum() > 0 && spentTimes100.compareTo(budget.multiply(WARNING_FROM)) >= 0) {
            return WARNING;
        }
        return ON_TRACK;
    }
}
