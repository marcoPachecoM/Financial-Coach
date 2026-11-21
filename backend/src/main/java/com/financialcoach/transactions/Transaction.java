package com.financialcoach.transactions;

import com.financialcoach.categorization.Category;
import com.financialcoach.categorization.CategorySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Maps the {@code transactions} table (V5 migration).
 *
 * <p>{@code @SQLRestriction} adds {@code deleted_at IS NULL} to every query Hibernate generates
 * for this entity, so soft-deleted rows behave as if they did not exist. Native SQL queries do
 * not get this filter and must add it themselves.
 */
@Entity
@Table(name = "transactions")
@SQLRestriction("deleted_at IS NULL")
class Transaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    /** Column "datetime"; named occurredAt in Java because DATETIME is a keyword in HQL. */
    @Column(name = "datetime", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private Category category;

    @Column(length = 100)
    private String categoryGroup;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorySource categorySource;

    @Column(updatable = false)
    private UUID importBatchId;

    /** char(64) in the schema; JdbcTypeCode makes Hibernate's schema validation expect CHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, updatable = false)
    private String fingerprint;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    protected Transaction() {
        // Required by JPA.
    }

    /** A transaction entered by hand (form, later WhatsApp). */
    static Transaction manual(UUID userId, UUID accountId, TransactionType type, LocalDateTime occurredAt,
                              String name, BigDecimal amount, Category category, CategorySource categorySource,
                              String categoryGroup, String notes) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.accountId = accountId;
        transaction.type = type;
        transaction.occurredAt = occurredAt;
        transaction.name = name;
        transaction.amount = amount;
        transaction.category = category;
        transaction.categorySource = categorySource;
        transaction.categoryGroup = categoryGroup;
        transaction.notes = notes;
        transaction.source = TransactionSource.MANUAL;
        return transaction;
    }

    /** A transaction from a CSV import. The fingerprint prevents importing the same row twice. */
    static Transaction fromCsv(UUID userId, UUID accountId, TransactionType type, LocalDateTime occurredAt,
                               String name, BigDecimal amount, Category category, CategorySource categorySource,
                               String categoryGroup, String notes, UUID importBatchId, String fingerprint) {
        Transaction transaction = manual(userId, accountId, type, occurredAt, name, amount,
                category, categorySource, categoryGroup, notes);
        transaction.source = TransactionSource.CSV;
        transaction.importBatchId = importBatchId;
        transaction.fingerprint = fingerprint;
        return transaction;
    }

    void changeType(TransactionType newType) {
        this.type = newType;
    }

    void changeOccurredAt(LocalDateTime newOccurredAt) {
        this.occurredAt = newOccurredAt;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeAmount(BigDecimal newAmount) {
        this.amount = newAmount;
    }

    /** A category chosen by the user always wins over automatic categorization. */
    void changeCategoryByUser(Category newCategory) {
        this.category = newCategory;
        this.categorySource = CategorySource.USER;
    }

    void changeCategoryGroup(String newCategoryGroup) {
        this.categoryGroup = newCategoryGroup;
    }

    void changeNotes(String newNotes) {
        this.notes = newNotes;
    }

    void moveTo(UUID newAccountId) {
        this.accountId = newAccountId;
    }

    void markDeleted(Instant when) {
        this.deletedAt = when;
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

    LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    String getName() {
        return name;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Category getCategory() {
        return category;
    }

    String getCategoryGroup() {
        return categoryGroup;
    }

    String getNotes() {
        return notes;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
