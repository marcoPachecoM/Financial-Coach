package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.categorization.CategoryGroupSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
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

    /** One of the closed list; assigned automatically when not given. */
    @Column(nullable = false, length = 50)
    private CategoryGroup categoryGroup;

    /** Free text inside the group, e.g. "Gas" in "Utilities". */
    @Column(length = 100)
    private String category;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryGroupSource categoryGroupSource;

    @Column(updatable = false)
    private UUID importBatchId;

    /** char(64) in the schema; JdbcTypeCode makes Hibernate's schema validation expect CHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, updatable = false)
    private String fingerprint;

    /** Rule this transaction is an occurrence of (V8); null when it is not recurring. */
    private UUID recurringTransactionId;

    /** Due date of the occurrence it covers; set together with recurringTransactionId. */
    private LocalDate recurrenceDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    /**
     * Tags (V9). Loaded with the transaction, because responses always include them and the open
     * session ends with the service call; {@code @BatchSize} loads the tags of a whole page in one
     * query instead of one per row.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "transaction_tags",
            joinColumns = @JoinColumn(name = "transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 100)
    private Set<Tag> tags = new HashSet<>();

    protected Transaction() {
        // Required by JPA.
    }

    /** A transaction entered by hand (form, later WhatsApp). */
    static Transaction manual(UUID userId, UUID accountId, TransactionType type, LocalDateTime occurredAt,
                              String name, BigDecimal amount, CategoryGroup categoryGroup,
                              CategoryGroupSource categoryGroupSource, String category, String notes) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.accountId = accountId;
        transaction.type = type;
        transaction.occurredAt = occurredAt;
        transaction.name = name;
        transaction.amount = amount;
        transaction.categoryGroup = categoryGroup;
        transaction.categoryGroupSource = categoryGroupSource;
        transaction.category = category;
        transaction.notes = notes;
        transaction.source = TransactionSource.MANUAL;
        return transaction;
    }

    /** A transaction from a CSV import. The fingerprint prevents importing the same row twice. */
    static Transaction fromCsv(UUID userId, UUID accountId, TransactionType type, LocalDateTime occurredAt,
                               String name, BigDecimal amount, CategoryGroup categoryGroup,
                               CategoryGroupSource categoryGroupSource, String category, String notes,
                               UUID importBatchId, String fingerprint) {
        Transaction transaction = manual(userId, accountId, type, occurredAt, name, amount,
                categoryGroup, categoryGroupSource, category, notes);
        transaction.source = TransactionSource.CSV;
        transaction.importBatchId = importBatchId;
        transaction.fingerprint = fingerprint;
        return transaction;
    }

    /** The occurrence of {@code rule} due on {@code dueDate}, created automatically. */
    static Transaction generated(RecurringTransaction rule, LocalDate dueDate) {
        Transaction transaction = manual(rule.getUserId(), rule.getAccountId(), rule.getType(),
                rule.occurrenceDateTime(dueDate), rule.getName(), rule.getAmount(), rule.getCategoryGroup(),
                CategoryGroupSource.USER, rule.getCategory(), rule.getNotes());
        transaction.source = TransactionSource.RECURRING;
        transaction.linkTo(rule, dueDate);
        return transaction;
    }

    /** Marks this transaction as the occurrence of {@code rule} due on {@code dueDate}. */
    void linkTo(RecurringTransaction rule, LocalDate dueDate) {
        this.recurringTransactionId = rule.getId();
        this.recurrenceDate = dueDate;
    }

    void unlink() {
        this.recurringTransactionId = null;
        this.recurrenceDate = null;
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

    /** A group chosen by the user always wins over automatic categorization. */
    void changeCategoryGroupByUser(CategoryGroup newCategoryGroup) {
        this.categoryGroup = newCategoryGroup;
        this.categoryGroupSource = CategoryGroupSource.USER;
    }

    void changeCategory(String newCategory) {
        this.category = newCategory;
    }

    void changeNotes(String newNotes) {
        this.notes = newNotes;
    }

    /** A rule belongs to one account, so moving the transaction ends its link to the rule. */
    void moveTo(UUID newAccountId) {
        if (!newAccountId.equals(accountId)) {
            unlink();
        }
        this.accountId = newAccountId;
    }

    void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    /** Replaces every tag; they must belong to the transaction's user (TagService resolves them). */
    void replaceTags(Collection<Tag> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    /**
     * Adds the tags it does not have yet, without going over the limit per transaction: an
     * occurrence of a recurring transaction gets the tags of the earlier ones this way.
     */
    void addTags(Collection<Tag> more) {
        Set<UUID> present = new HashSet<>();
        tags.forEach(tag -> present.add(tag.getId()));
        for (Tag tag : more) {
            if (tags.size() >= TagService.MAX_PER_TRANSACTION) {
                return;
            }
            if (present.add(tag.getId())) {
                tags.add(tag);
            }
        }
    }

    Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /** Tag names in alphabetical order, ignoring case. */
    List<String> tagNames() {
        return tags.stream().map(Tag::getName)
                .sorted(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT)))
                .toList();
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

    CategoryGroup getCategoryGroup() {
        return categoryGroup;
    }

    String getCategory() {
        return category;
    }

    String getNotes() {
        return notes;
    }

    TransactionSource getSource() {
        return source;
    }

    UUID getRecurringTransactionId() {
        return recurringTransactionId;
    }

    LocalDate getRecurrenceDate() {
        return recurrenceDate;
    }

    boolean isGenerated() {
        return source == TransactionSource.RECURRING;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
