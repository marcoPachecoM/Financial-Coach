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
import org.hibernate.annotations.UuidGenerator;

/** Maps the {@code goal_contributions} table (V11). Negative amounts are withdrawals. */
@Entity
@Table(name = "goal_contributions")
class GoalContribution {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID goalId;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDate contributedOn;

    @Column(length = 200, updatable = false)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GoalContribution() {
    }

    GoalContribution(UUID goalId, BigDecimal amount, LocalDate contributedOn, String note) {
        this.goalId = goalId;
        this.amount = amount;
        this.contributedOn = contributedOn;
        this.note = note;
    }

    UUID getId() {
        return id;
    }

    UUID getGoalId() {
        return goalId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    LocalDate getContributedOn() {
        return contributedOn;
    }

    String getNote() {
        return note;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    GoalProjection.Entry toEntry() {
        return new GoalProjection.Entry(contributedOn, amount);
    }
}
