package com.financialcoach.analytics;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.Money;
import java.util.List;
import java.util.UUID;

/** {@code CategoryBudgets} schema of openapi.yaml. Money as strings with 2 decimals. */
record CategoryBudgetsResponse(UUID accountId, String month, List<Budget> budgets, List<Unbudgeted> unbudgeted) {

    /** {@code percentage_used} is null for a zero budget. */
    record Budget(CategoryGroup categoryGroup, String budget, String spent, String remaining, String percentageUsed,
                  BudgetStatus status) {
    }

    record Unbudgeted(CategoryGroup categoryGroup, String spent, long count) {
    }

    static CategoryBudgetsResponse from(UUID accountId, CategoryBudgetReport report) {
        List<Budget> budgets = report.budgets().stream()
                .map(usage -> new Budget(usage.categoryGroup(), Money.format(usage.budget()),
                        Money.format(usage.spent()), Money.format(usage.remaining()),
                        usage.percentageUsed() == null ? null : usage.percentageUsed().toPlainString(),
                        usage.status()))
                .toList();
        List<Unbudgeted> unbudgeted = report.unbudgeted().stream()
                .map(total -> new Unbudgeted(total.categoryGroup(), Money.format(total.amount()), total.count()))
                .toList();
        return new CategoryBudgetsResponse(accountId, report.month().toString(), budgets, unbudgeted);
    }
}
