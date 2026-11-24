package com.financialcoach.goals;

import com.financialcoach.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code GoalContribution} schema of openapi.yaml. */
record GoalContributionResponse(UUID id, String amount, LocalDate date, String note, Instant createdAt) {

    static GoalContributionResponse from(GoalContribution contribution) {
        return new GoalContributionResponse(contribution.getId(), Money.format(contribution.getAmount()),
                contribution.getContributedOn(), contribution.getNote(), contribution.getCreatedAt());
    }
}
