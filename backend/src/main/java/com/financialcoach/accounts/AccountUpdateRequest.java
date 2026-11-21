package com.financialcoach.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financialcoach.shared.Money;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /accounts/{id}} ({@code AccountUpdateRequest} in openapi.yaml).
 *
 * <p>A PATCH must tell apart "field not sent" (keep the value) from "field sent as null"
 * (clear the value). A record cannot, because both arrive as null. Here each setter records
 * that its field was present. Accessors are deliberately not named getX/isX, so Jackson does
 * not treat the "provided" flags as JSON properties.
 */
final class AccountUpdateRequest {

    @Size(min = 1, max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;
    private boolean nameProvided;

    @Pattern(regexp = Money.POSITIVE_PATTERN, message = Money.POSITIVE_MESSAGE)
    private String monthlyBudget;
    private boolean monthlyBudgetProvided;

    @JsonProperty("name")
    void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonProperty("monthly_budget")
    void setMonthlyBudget(String monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
        this.monthlyBudgetProvided = true;
    }

    String name() {
        return name;
    }

    boolean nameProvided() {
        return nameProvided;
    }

    String monthlyBudget() {
        return monthlyBudget;
    }

    boolean monthlyBudgetProvided() {
        return monthlyBudgetProvided;
    }

    boolean hasNoChanges() {
        return !nameProvided && !monthlyBudgetProvided;
    }
}
