package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Totals of one day that has transactions: the expenses (count and positive sum) and how many
 * transactions of any type. Days without transactions have no entry.
 */
public record DailyTotal(LocalDate date, long expenseCount, BigDecimal expenseTotal, long transactionCount) {

    /** Constructor used by the JPQL query (count and sum return Long). */
    DailyTotal(LocalDate date, Long expenseCount, BigDecimal expenseTotal, Long transactionCount) {
        this(date, expenseCount.longValue(), expenseTotal, transactionCount.longValue());
    }
}
