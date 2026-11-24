package com.financialcoach.goals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Maps the {@code savings_goals} table (V11). */
@Entity
@Table(name = "savings_goals")
class SavingsGoal {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    private LocalDate targetDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected SavingsGoal() {
    }

    SavingsGoal(UUID userId, String name, BigDecimal targetAmount, LocalDate targetDate) {
        this.userId = userId;
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeTargetAmount(BigDecimal newTarget) {
        this.targetAmount = newTarget;
    }

    /** {@code null} removes the date. */
    void changeTargetDate(LocalDate newDate) {
        this.targetDate = newDate;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    BigDecimal getTargetAmount() {
        return targetAmount;
    }

    LocalDate getTargetDate() {
        return targetDate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
