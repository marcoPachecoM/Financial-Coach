package com.financialcoach.transactions;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * {@code recurrence} of {@code POST /transactions}: repeat this transaction automatically.
 * {@code endDate} is optional (no end); it must not be before the transaction's date.
 */
record RecurrenceRequest(
        @NotNull
        RecurrenceFrequency frequency,

        LocalDate endDate
) {
}
