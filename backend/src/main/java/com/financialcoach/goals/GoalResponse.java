package com.financialcoach.goals;

import com.financialcoach.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code Goal} schema of openapi.yaml. Money as strings with 2 decimals. */
record GoalResponse(UUID id, String name, String targetAmount, LocalDate targetDate, String saved, String remaining,
                    String percentage, String monthlyPace, String requiredMonthly, LocalDate projectedDate,
                    GoalStatus status, Instant createdAt) {

    static GoalResponse from(SavingsGoal goal, GoalProjection projection) {
        return new GoalResponse(goal.getId(), goal.getName(), Money.format(goal.getTargetAmount()),
                goal.getTargetDate(), Money.format(projection.saved()), Money.format(projection.remaining()),
                projection.percentage().toPlainString(), Money.format(projection.monthlyPace()),
                projection.requiredMonthly() == null ? null : Money.format(projection.requiredMonthly()),
                projection.projectedDate(), projection.status(), goal.getCreatedAt());
    }
}
