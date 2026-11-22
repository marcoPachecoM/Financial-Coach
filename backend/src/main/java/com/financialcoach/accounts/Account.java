package com.financialcoach.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps the {@code accounts} table (V2 migration).
 *
 * <p>The owner is referenced by id only, not as a JPA relation to the users module: modules
 * talk to each other through ids and public services, never through each other's entities.
 */
@Entity
@Table(name = "accounts")
class Account {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(precision = 14, scale = 2)
    private BigDecimal monthlyBudget;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {
        // Required by JPA.
    }

    Account(UUID userId, String name, BigDecimal monthlyBudget) {
        this.userId = userId;
        this.name = name;
        this.monthlyBudget = monthlyBudget;
    }

    void rename(String newName) {
        this.name = newName;
    }

    /** {@code null} removes the budget. */
    void changeMonthlyBudget(BigDecimal newBudget) {
        this.monthlyBudget = newBudget;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getName() {
        return name;
    }

    BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
