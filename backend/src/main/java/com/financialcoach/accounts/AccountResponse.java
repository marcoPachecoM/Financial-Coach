package com.financialcoach.accounts;

import com.financialcoach.shared.Money;
import java.time.Instant;
import java.util.UUID;

/** {@code Account} schema of openapi.yaml. {@code monthly_budget} is always present, null when unset. */
record AccountResponse(UUID id, String name, String monthlyBudget, Instant createdAt, Instant updatedAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                Money.format(account.getMonthlyBudget()),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
