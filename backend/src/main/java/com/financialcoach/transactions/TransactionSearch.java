package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of {@link TransactionStatistics#search}: totals over every match, but only the newest
 * {@code latest} rows. Totals are positive.
 */
public record TransactionSearch(long expenseCount, BigDecimal expenseTotal, long incomeCount, BigDecimal incomeTotal,
                                List<TransactionLine> latest) {

    public long matches() {
        return expenseCount + incomeCount;
    }
}
