package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps the {@code recurring_transactions} table (V8 migration): a rule from which the next
 * occurrences of a transaction are created automatically.
 *
 * <p>{@code nextDueDate} is the next occurrence to create; it only moves forward, so an
 * occurrence the user deleted is not created again.
 */
@Entity
@Table(name = "recurring_transactions")
class RecurringTransaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TransactionType type;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private CategoryGroup categoryGroup;

    @Column(length = 100)
    private String category;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private RecurrenceFrequency frequency;

    @Column(nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(nullable = false, updatable = false)
    private LocalTime timeOfDay;

    private LocalDate endDate;

    private LocalDate nextDueDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected RecurringTransaction() {
        // Required by JPA.
    }

    /** A rule whose occurrence 0 is {@code first}; the next occurrences copy its data. */
    static RecurringTransaction from(Transaction first, RecurrenceFrequency frequency, LocalDate endDate) {
        RecurringTransaction rule = new RecurringTransaction();
        rule.userId = first.getUserId();
        rule.accountId = first.getAccountId();
        rule.type = first.getType();
        rule.name = first.getName();
        rule.amount = first.getAmount();
        rule.categoryGroup = first.getCategoryGroup();
        rule.category = first.getCategory();
        rule.notes = first.getNotes();
        rule.frequency = frequency;
        rule.startDate = first.getOccurredAt().toLocalDate();
        rule.timeOfDay = first.getOccurredAt().toLocalTime();
        rule.endDate = endDate;
        rule.nextDueDate = rule.dueAfter(rule.startDate);
        return rule;
    }

    /** True when {@code date} is a day an occurrence may fall on (on or before the end date). */
    boolean isWithinEnd(LocalDate date) {
        return endDate == null || !date.isAfter(endDate);
    }

    /** Called after creating (or skipping) the occurrence due on {@code nextDueDate}. */
    void advance() {
        nextDueDate = dueAfter(nextDueDate);
    }

    /**
     * Sets or clears the end date; occurrences after it are no longer created. To restart a rule
     * that had already ended, call {@link #resumeAfter} afterwards.
     */
    void changeEndDate(LocalDate newEndDate) {
        this.endDate = newEndDate;
        if (nextDueDate != null && !isWithinEnd(nextDueDate)) {
            nextDueDate = null;
        }
    }

    /**
     * Resumes a rule that had ended, from the first occurrence after {@code today}: occurrences
     * that were skipped while it was stopped are not created.
     */
    void resumeAfter(LocalDate today) {
        if (nextDueDate == null) {
            nextDueDate = dueAfter(today.isBefore(startDate) ? startDate : today);
        }
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeAmount(BigDecimal newAmount) {
        this.amount = newAmount;
    }

    void changeCategoryGroup(CategoryGroup newCategoryGroup) {
        this.categoryGroup = newCategoryGroup;
    }

    void changeCategory(String newCategory) {
        this.category = newCategory;
    }

    void changeNotes(String newNotes) {
        this.notes = newNotes;
    }

    /** Local date-time of the generated transaction for the occurrence due on {@code dueDate}. */
    LocalDateTime occurrenceDateTime(LocalDate dueDate) {
        return dueDate.atTime(timeOfDay);
    }

    /** First due date after {@code date}, or null once past the end date. */
    private LocalDate dueAfter(LocalDate date) {
        LocalDate next = frequency.nextAfter(startDate, date);
        return isWithinEnd(next) ? next : null;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getAccountId() {
        return accountId;
    }

    TransactionType getType() {
        return type;
    }

    String getName() {
        return name;
    }

    BigDecimal getAmount() {
        return amount;
    }

    CategoryGroup getCategoryGroup() {
        return categoryGroup;
    }

    String getCategory() {
        return category;
    }

    String getNotes() {
        return notes;
    }

    RecurrenceFrequency getFrequency() {
        return frequency;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    LocalDate getNextDueDate() {
        return nextDueDate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
