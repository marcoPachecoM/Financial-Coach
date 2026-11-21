package com.financialcoach.accounts;

import com.financialcoach.shared.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /accounts} ({@code AccountCreateRequest} in openapi.yaml). */
record AccountCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        /* Omitted or null means no budget. */
        @Pattern(regexp = Money.POSITIVE_PATTERN, message = Money.POSITIVE_MESSAGE)
        String monthlyBudget
) {
}
