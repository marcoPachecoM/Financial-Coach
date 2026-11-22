package com.financialcoach.analytics;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.transactions.TransactionStatistics;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly budget usage of an account. Lives in analytics, not in accounts, because it needs
 * the transactions module, which already depends on accounts (a cycle Spring cannot build).
 */
@Service
class BudgetService {

    private static final BigDecimal WARNING_FROM = BigDecimal.valueOf(80);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AccountService accountService;
    private final TransactionStatistics statistics;

    BudgetService(AccountService accountService, TransactionStatistics statistics) {
        this.accountService = accountService;
        this.statistics = statistics;
    }

    /** Only expenses count; income does not offset spending. */
    @Transactional(readOnly = true)
    BudgetUsageResponse usage(UUID userId, UUID accountId, YearMonth month) {
        Optional<BigDecimal> budget = accountService.monthlyBudget(userId, accountId);
        BigDecimal spent = statistics.totals(userId, accountId, DateRange.of(month)).expenseTotal();

        if (budget.isEmpty()) {
            return new BudgetUsageResponse(accountId, month.toString(), null, Money.format(spent), null, null,
                    BudgetStatus.NO_BUDGET);
        }
        BigDecimal limit = budget.get();
        return new BudgetUsageResponse(
                accountId,
                month.toString(),
                Money.format(limit),
                Money.format(spent),
                Money.format(limit.subtract(spent)),
                limit.signum() == 0 ? null : Percentages.of(spent, limit).toPlainString(),
                status(spent, limit));
    }

    /**
     * Compared on exact amounts, not on the rounded percentage: 79.999 % is still ON_TRACK.
     * A zero budget is EXCEEDED as soon as anything is spent.
     */
    private static BudgetStatus status(BigDecimal spent, BigDecimal budget) {
        BigDecimal spentTimes100 = spent.multiply(HUNDRED);
        if (spentTimes100.compareTo(budget.multiply(HUNDRED)) > 0) {
            return BudgetStatus.EXCEEDED;
        }
        if (budget.signum() > 0 && spentTimes100.compareTo(budget.multiply(WARNING_FROM)) >= 0) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.ON_TRACK;
    }
}
