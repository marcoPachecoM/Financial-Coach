package com.financialcoach.analytics;

/** {@code AlertType} of openapi.yaml. Each type has its own fields in {@code data}. */
enum AlertType {
    BUDGET_EXCEEDED,
    BUDGET_WARNING,
    CATEGORY_BUDGET_EXCEEDED,
    CATEGORY_BUDGET_WARNING,
    RECURRING_MISSING,
    RECURRING_PRICE_CHANGED,
    RECURRING_UPCOMING,
    SUBSCRIPTION_AUDIT,
    RECURRING_SUGGESTED
}
