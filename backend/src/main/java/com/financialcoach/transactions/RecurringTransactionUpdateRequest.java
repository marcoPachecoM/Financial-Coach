package com.financialcoach.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.Money;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PATCH /recurring-transactions/{id}}. Same technique as TransactionUpdateRequest:
 * "not sent" (keep) and "sent as null" (clear) are told apart. Only category, notes and
 * end_date may be cleared. Changes apply to the occurrences created from now on.
 */
final class RecurringTransactionUpdateRequest {

    @Size(min = 1, max = 255)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;
    private boolean nameProvided;

    @Pattern(regexp = Money.SIGNED_PATTERN, message = Money.SIGNED_MESSAGE)
    private String amount;
    private boolean amountProvided;

    private CategoryGroup categoryGroup;
    private boolean categoryGroupProvided;

    @Size(max = 100)
    private String category;
    private boolean categoryProvided;

    @Size(max = 1000)
    private String notes;
    private boolean notesProvided;

    private LocalDate endDate;
    private boolean endDateProvided;

    @JsonProperty("name")
    void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonProperty("amount")
    void setAmount(String amount) {
        this.amount = amount;
        this.amountProvided = true;
    }

    @JsonProperty("category_group")
    void setCategoryGroup(CategoryGroup categoryGroup) {
        this.categoryGroup = categoryGroup;
        this.categoryGroupProvided = true;
    }

    @JsonProperty("category")
    void setCategory(String category) {
        this.category = category;
        this.categoryProvided = true;
    }

    @JsonProperty("notes")
    void setNotes(String notes) {
        this.notes = notes;
        this.notesProvided = true;
    }

    @JsonProperty("end_date")
    void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDateProvided = true;
    }

    String name() { return name; }
    boolean nameProvided() { return nameProvided; }

    String amount() { return amount; }
    boolean amountProvided() { return amountProvided; }

    CategoryGroup categoryGroup() { return categoryGroup; }
    boolean categoryGroupProvided() { return categoryGroupProvided; }

    String category() { return category; }
    boolean categoryProvided() { return categoryProvided; }

    String notes() { return notes; }
    boolean notesProvided() { return notesProvided; }

    LocalDate endDate() { return endDate; }
    boolean endDateProvided() { return endDateProvided; }

    boolean hasNoChanges() {
        return !nameProvided && !amountProvided && !categoryGroupProvided && !categoryProvided
                && !notesProvided && !endDateProvided;
    }
}
