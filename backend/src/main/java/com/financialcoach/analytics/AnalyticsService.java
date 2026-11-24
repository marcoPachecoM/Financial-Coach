package com.financialcoach.analytics;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.transactions.CategoryGroupTotal;
import com.financialcoach.transactions.DateSpan;
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
        DateSpan span = statistics.dateSpan(userId, accountId, range);

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
                Money.format(totals.largestExpense()),
                span.first() == null ? null : span.first().toLocalDate(),
                span.last() == null ? null : span.last().toLocalDate());
    }

    /** Days with transactions, oldest first; with {@code categoryGroup}, only that group counts. */
    List<DailyTotalResponse> daily(UUID userId, UUID accountId, DateRange range, CategoryGroup categoryGroup) {
        return statistics.dailyTotals(userId, accountId, range, categoryGroup).stream()
                .map(day -> new DailyTotalResponse(day.date(), Money.format(day.expenseTotal()), day.expenseCount(),
                        day.transactionCount()))
                .toList();
    }

    /** Percentages are of the total of the selected type; they may not add up to exactly 100 after rounding. */
    List<CategoryGroupBreakdownResponse> byCategoryGroup(UUID userId, UUID accountId, TransactionType type,
                                                         DateRange range) {
        List<CategoryGroupTotal> groups = statistics.totalsByCategoryGroup(userId, accountId, type, range);
        BigDecimal total = groups.stream().map(CategoryGroupTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return groups.stream()
                .map(group -> new CategoryGroupBreakdownResponse(
                        group.categoryGroup(),
                        Money.format(group.amount()),
                        Percentages.of(group.amount(), total).toPlainString(),
                        group.count()))
                .toList();
    }
}
