package com.financialcoach.analytics;

import java.time.LocalDate;

/** {@code DailyTotal} schema of openapi.yaml. Money as a string with 2 decimals. */
record DailyTotalResponse(LocalDate date, String expenseTotal, long expenseCount, long transactionCount) {
}
