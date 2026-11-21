package com.financialcoach.analytics;

import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.transactions.CategoryTotal;
import com.financialcoach.transactions.TransactionStatistics;
import com.financialcoach.transactions.TransactionTotals;
import com.financialcoach.transactions.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Analytics endpoints: turns the totals computed by the transactions module into the
 * figures of the contract (net, average, percentages).
 */
@Service
class AnalyticsService {

    private final TransactionStatistics statistics;

    AnalyticsService(TransactionStatistics statistics) {
        this.statistics = statistics;
    }

    AnalyticsSummaryResponse summary(UUID userId, UUID accountId, DateRange range) {
        TransactionTotals totals = statistics.totals(userId, accountId, range);

        BigDecimal average = totals.expenseCount() == 0
                ? BigDecimal.ZERO
                : totals.expenseTotal().divide(BigDecimal.valueOf(totals.expenseCount()), 2, RoundingMode.HALF_UP);

        return new AnalyticsSummaryResponse(
                Money.format(totals.expenseTotal()),
                Money.format(totals.incomeTotal()),
                Money.format(totals.incomeTotal().subtract(totals.expenseTotal())),
                totals.expenseCount(),
                totals.incomeCount(),
                Money.format(average),
                Money.format(totals.largestExpense()));
    }

    /** Percentages are of the total of the selected type; they may not add up to exactly 100 after rounding. */
    List<CategoryBreakdownResponse> byCategory(UUID userId, UUID accountId, TransactionType type, DateRange range) {
        List<CategoryTotal> categories = statistics.totalsByCategory(userId, accountId, type, range);
        BigDecimal total = categories.stream().map(CategoryTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return categories.stream()
                .map(category -> new CategoryBreakdownResponse(
                        category.category(),
                        Money.format(category.amount()),
                        Percentages.of(category.amount(), total).toPlainString(),
                        category.count()))
                .toList();
    }
}
