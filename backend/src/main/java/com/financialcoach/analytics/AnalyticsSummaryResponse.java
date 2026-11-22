package com.financialcoach.analytics;

/** {@code AnalyticsSummary} schema of openapi.yaml. Money as strings with 2 decimals. */
record AnalyticsSummaryResponse(String totalExpenses, String totalIncome, String net, long expenseCount,
                                long incomeCount, String averageExpense, String largestExpense) {
}
