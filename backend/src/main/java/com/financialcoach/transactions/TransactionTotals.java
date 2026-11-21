package com.financialcoach.transactions;

import java.math.BigDecimal;

/**
 * Totals of an account's transactions in a period. All amounts are positive (income is stored
 * negative, but reported here as a positive figure). Amounts are zero when there are no rows.
 */
public record TransactionTotals(long expenseCount, BigDecimal expenseTotal, BigDecimal largestExpense,
                                long incomeCount, BigDecimal incomeTotal) {
}
