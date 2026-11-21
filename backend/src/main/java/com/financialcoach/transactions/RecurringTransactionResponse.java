package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code RecurringTransaction} schema of openapi.yaml. {@code next_due_date} is null once the
 * rule has ended (past its end date or stopped).
 */
record RecurringTransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        String name,
        String amount,
        CategoryGroup categoryGroup,
        String category,
        String notes,
        RecurrenceFrequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextDueDate,
        Instant createdAt,
        Instant updatedAt
) {

    static RecurringTransactionResponse from(RecurringTransaction rule) {
        return new RecurringTransactionResponse(
                rule.getId(),
                rule.getAccountId(),
                rule.getType(),
                rule.getName(),
                Money.format(rule.getAmount()),
                rule.getCategoryGroup(),
                rule.getCategory(),
                rule.getNotes(),
                rule.getFrequency(),
                rule.getStartDate(),
                rule.getEndDate(),
                rule.getNextDueDate(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
