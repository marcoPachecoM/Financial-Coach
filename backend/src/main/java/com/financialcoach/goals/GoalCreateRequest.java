package com.financialcoach.goals;

import com.financialcoach.shared.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of {@code POST /goals} ({@code GoalCreateRequest} in openapi.yaml). */
record GoalCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = Money.POSITIVE_PATTERN, message = Money.POSITIVE_MESSAGE) String targetAmount,
        /* Optional; not in the past. */
        LocalDate targetDate
) {
}
