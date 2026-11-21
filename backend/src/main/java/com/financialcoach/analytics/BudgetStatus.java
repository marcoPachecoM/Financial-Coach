package com.financialcoach.analytics;

/** {@code BudgetUsage.status} of openapi.yaml. */
enum BudgetStatus {
    /** Below 80 % of the budget. */
    ON_TRACK,
    /** From 80 % to 100 % (inclusive). */
    WARNING,
    /** Above 100 %. */
    EXCEEDED,
    /** The account has no monthly budget. */
    NO_BUDGET
}
