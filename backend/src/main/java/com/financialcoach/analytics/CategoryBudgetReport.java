package com.financialcoach.analytics;

import com.financialcoach.transactions.CategoryGroupTotal;
import java.time.YearMonth;
import java.util.List;

/**
 * Category budgets of an account for a month: the budgeted groups, most used first, and the
 * expenses of the groups without a budget, largest first (so no spending goes unseen).
 */
public record CategoryBudgetReport(YearMonth month, List<CategoryBudgetUsage> budgets,
                                   List<CategoryGroupTotal> unbudgeted) {
}
