package com.financialcoach.transactions;

import com.financialcoach.categorization.Category;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One valid data row of an uploaded CSV ({@code CsvTransactionRow} in openapi.yaml), already
 * converted to domain types. Optional text is null when empty; {@code category} is null when the
 * row has none and must be categorized automatically.
 *
 * @param row data row number, starting at 1 (header excluded), used in error messages
 */
record CsvRow(int row, TransactionType type, LocalDateTime occurredAt, String name, BigDecimal amount,
              String categoryGroup, Category category, String account, String notes) {
}
