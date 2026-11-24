package com.financialcoach.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps the {@code tags} table (V9): a label of one user, shared by their transactions. Created only
 * by {@link TagRepository#insertIfAbsent}, so the database fills in id and created_at.
 */
@Entity
@Table(name = "tags")
class Tag {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
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
}
