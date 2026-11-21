package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.DateRange;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates over an account's transactions, for other modules (analytics, budgets, later the
 * copilot). The aggregation runs in the database; soft-deleted rows are excluded.
 * Every method fails with 404 unless the account belongs to the user.
 */
@Service
public class TransactionStatistics {

    private final TransactionRepository transactions;
    private final AccountService accountService;

    TransactionStatistics(TransactionRepository transactions, AccountService accountService) {
        this.transactions = transactions;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public TransactionTotals totals(UUID userId, UUID accountId, DateRange range) {
        accountService.requireOwned(userId, accountId);

        TypeTotals expenses = empty(TransactionType.EXPENSE);
        TypeTotals income = empty(TransactionType.INCOME);
        for (TypeTotals totals : transactions.totalsByType(userId, accountId, range.from(), range.to())) {
            if (totals.type() == TransactionType.EXPENSE) {
                expenses = totals;
            } else {
                income = totals;
            }
        }
        return new TransactionTotals(
                expenses.count(), expenses.sum(), expenses.largest(),
                income.count(), income.sum().negate());
    }

    /** One entry per category group with transactions of this type, largest amount first. */
    @Transactional(readOnly = true)
    public List<CategoryGroupTotal> totalsByCategoryGroup(UUID userId, UUID accountId, TransactionType type,
                                                          DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.totalsByCategoryGroup(userId, accountId, type, range.from(), range.to());
    }

    private static TypeTotals empty(TransactionType type) {
        return new TypeTotals(type, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
