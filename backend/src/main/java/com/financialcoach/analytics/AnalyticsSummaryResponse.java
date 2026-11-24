package com.financialcoach.analytics;

import java.time.LocalDate;

/**
 * {@code AnalyticsSummary} schema of openapi.yaml. Money as strings with 2 decimals. The dates of
 * the first and last transaction of the period are null when it has none.
 */
record AnalyticsSummaryResponse(String totalExpenses, String totalIncome, String net, long expenseCount,
                                long incomeCount, String averageExpense, String largestExpense,
                                LocalDate firstDate, LocalDate lastDate) {
}
