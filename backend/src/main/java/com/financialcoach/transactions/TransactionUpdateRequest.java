package com.financialcoach.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financialcoach.categorization.Category;
import com.financialcoach.shared.Money;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Body of {@code PATCH /transactions/{id}} ({@code TransactionUpdateRequest} in openapi.yaml).
 *
 * <p>Same technique as AccountUpdateRequest: each setter records that its field was sent, so
 * "not sent" (keep) and "sent as null" (clear) can be told apart. Only category_group and
 * notes may be cleared; null for any other field is rejected by TransactionService.
 */
final class TransactionUpdateRequest {

    private TransactionType type;
    private boolean typeProvided;

    private LocalDateTime datetime;
    private boolean datetimeProvided;

    @Size(min = 1, max = 255)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;
    private boolean nameProvided;

    @Pattern(regexp = Money.SIGNED_PATTERN, message = Money.SIGNED_MESSAGE)
    private String amount;
    private boolean amountProvided;

    @Size(max = 100)
    private String categoryGroup;
    private boolean categoryGroupProvided;

    private Category category;
    private boolean categoryProvided;

    private UUID accountId;
    private boolean accountIdProvided;

    @Size(max = 1000)
    private String notes;
    private boolean notesProvided;

    @JsonProperty("type")
    void setType(TransactionType type) {
        this.type = type;
        this.typeProvided = true;
    }

    @JsonProperty("datetime")
    void setDatetime(LocalDateTime datetime) {
        this.datetime = datetime;
        this.datetimeProvided = true;
    }

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
    void setCategoryGroup(String categoryGroup) {
        this.categoryGroup = categoryGroup;
        this.categoryGroupProvided = true;
    }

    @JsonProperty("category")
    void setCategory(Category category) {
        this.category = category;
        this.categoryProvided = true;
    }

    @JsonProperty("account_id")
    void setAccountId(UUID accountId) {
        this.accountId = accountId;
        this.accountIdProvided = true;
    }

    @JsonProperty("notes")
    void setNotes(String notes) {
        this.notes = notes;
        this.notesProvided = true;
    }

    TransactionType type() { return type; }
    boolean typeProvided() { return typeProvided; }

    LocalDateTime datetime() { return datetime; }
    boolean datetimeProvided() { return datetimeProvided; }

    String name() { return name; }
    boolean nameProvided() { return nameProvided; }

    String amount() { return amount; }
    boolean amountProvided() { return amountProvided; }

    String categoryGroup() { return categoryGroup; }
    boolean categoryGroupProvided() { return categoryGroupProvided; }

    Category category() { return category; }
    boolean categoryProvided() { return categoryProvided; }

    UUID accountId() { return accountId; }
    boolean accountIdProvided() { return accountIdProvided; }

    String notes() { return notes; }
    boolean notesProvided() { return notesProvided; }

    boolean hasNoChanges() {
        return !typeProvided && !datetimeProvided && !nameProvided && !amountProvided
                && !categoryGroupProvided && !categoryProvided && !accountIdProvided && !notesProvided;
    }
}
