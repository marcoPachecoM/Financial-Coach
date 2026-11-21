package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.Money;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code Transaction} schema of openapi.yaml. Optional fields are always present, null when unset.
 * {@code recurring_transaction_id} is the rule the transaction is an occurrence of.
 */
record TransactionResponse(
        UUID id,
        UUID userId,
        UUID accountId,
        TransactionType type,
        LocalDateTime datetime,
        String name,
        String amount,
        CategoryGroup categoryGroup,
        String category,
        String notes,
        UUID recurringTransactionId,
        Instant createdAt,
        Instant updatedAt
) {

    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getOccurredAt(),
                transaction.getName(),
                Money.format(transaction.getAmount()),
                transaction.getCategoryGroup(),
                transaction.getCategory(),
                transaction.getNotes(),
                transaction.getRecurringTransactionId(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
