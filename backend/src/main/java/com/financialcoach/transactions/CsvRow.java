package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One valid data row of an uploaded CSV ({@code CsvTransactionRow} in openapi.yaml), already
 * converted to domain types. Optional text is null when empty; {@code categoryGroup} is null when
 * the row has none and must be assigned automatically.
 *
 * @param row data row number, starting at 1 (header excluded), used in error messages
 */
record CsvRow(int row, TransactionType type, LocalDateTime occurredAt, String name, BigDecimal amount,
              CategoryGroup categoryGroup, String category, String account, String notes) {
}
