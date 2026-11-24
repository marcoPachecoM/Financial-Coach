package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** The columns of an exported transaction, read without loading the entity (tags come apart). */
record CsvExportRow(UUID id, LocalDateTime occurredAt, TransactionType type, String name, BigDecimal amount,
                    CategoryGroup categoryGroup, String category, UUID accountId, String notes) {
}
