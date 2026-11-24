package com.financialcoach.analytics;

import com.financialcoach.shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PUT /accounts/{account_id}/category-budgets}: every budget of the account; the
 * groups left out have no budget any more, and an empty list removes them all.
 */
record CategoryBudgetsRequest(@NotNull @Size(max = 26) List<@Valid @NotNull Item> budgets) {

    record Item(
            /* One of the 26 labels, e.g. "Food". */
            @NotBlank String categoryGroup,
            @NotNull @Pattern(regexp = Money.POSITIVE_PATTERN, message = Money.POSITIVE_MESSAGE) String monthlyAmount) {
    }
}
