package com.financialcoach.analytics;

import java.util.UUID;

/**
 * {@code BudgetUsage} schema of openapi.yaml. {@code budget}, {@code remaining} and
 * {@code percentage_used} are always present, null when they do not apply.
 */
record BudgetUsageResponse(UUID accountId, String month, String budget, String spent, String remaining,
                           String percentageUsed, BudgetStatus status) {
}
