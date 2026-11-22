package com.financialcoach.transactions;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /recurring-transactions}: makes an existing transaction recurring. The rule
 * copies its data and the transaction becomes occurrence 0.
 */
record RecurringTransactionCreateRequest(
        @NotNull
        UUID transactionId,

        @NotNull
        RecurrenceFrequency frequency,

        LocalDate endDate
) {
}
