package com.financialcoach.transactions;

import com.financialcoach.categorization.Category;
import com.financialcoach.shared.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Body of {@code POST /transactions} ({@code TransactionCreateRequest} in openapi.yaml).
 * The sign rule (EXPENSE positive, INCOME negative) depends on two fields, so it is checked in
 * TransactionService rather than with annotations.
 */
record TransactionCreateRequest(
        @NotNull
        TransactionType type,

        /* Value of the form's datetime-local input; seconds are optional. */
        @NotNull
        LocalDateTime datetime,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        @Pattern(regexp = Money.SIGNED_PATTERN, message = Money.SIGNED_MESSAGE)
        String amount,

        @Size(max = 100)
        String categoryGroup,

        /* Omitted: categorized automatically. */
        Category category,

        @NotNull
        UUID accountId,

        @Size(max = 1000)
        String notes
) {
}
