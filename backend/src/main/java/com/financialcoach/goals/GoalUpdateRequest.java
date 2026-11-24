package com.financialcoach.goals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financialcoach.shared.Money;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PATCH /goals/{id}} ({@code GoalUpdateRequest} in openapi.yaml). Like
 * AccountUpdateRequest, each setter records that its field was sent, so {@code "target_date": null}
 * (remove the date) is told apart from a missing field (keep it).
 */
final class GoalUpdateRequest {

    @Size(min = 1, max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;
    private boolean nameProvided;

    @Pattern(regexp = Money.POSITIVE_PATTERN, message = Money.POSITIVE_MESSAGE)
    private String targetAmount;
    private boolean targetAmountProvided;

    private LocalDate targetDate;
    private boolean targetDateProvided;

    @JsonProperty("name")
    void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonProperty("target_amount")
    void setTargetAmount(String targetAmount) {
        this.targetAmount = targetAmount;
        this.targetAmountProvided = true;
    }

    @JsonProperty("target_date")
    void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
        this.targetDateProvided = true;
    }

    String name() {
        return name;
    }

    boolean nameProvided() {
        return nameProvided;
    }

    String targetAmount() {
        return targetAmount;
    }

    boolean targetAmountProvided() {
        return targetAmountProvided;
    }

    LocalDate targetDate() {
        return targetDate;
    }

    boolean targetDateProvided() {
        return targetDateProvided;
    }

    boolean hasNoChanges() {
        return !nameProvided && !targetAmountProvided && !targetDateProvided;
    }
}
