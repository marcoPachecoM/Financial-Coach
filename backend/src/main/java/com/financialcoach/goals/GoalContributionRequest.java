package com.financialcoach.goals;

import com.financialcoach.shared.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of {@code POST /goals/{id}/contributions}. A negative amount is a withdrawal. */
record GoalContributionRequest(
        @NotNull @Pattern(regexp = Money.SIGNED_PATTERN, message = Money.SIGNED_MESSAGE) String amount,
        /* Optional, default today; not in the future. */
        LocalDate date,
        @Size(max = 200) String note
) {
}
