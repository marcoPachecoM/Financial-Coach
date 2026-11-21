package com.financialcoach.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps the {@code refresh_tokens} table (V1 migration): server-side state of each refresh token,
 * so tokens can be revoked (logout), rotated and their reuse detected. Only the {@code jti} is
 * stored, never the token itself.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 64, updatable = false)
    private String jti;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(length = 64)
    private String replacedByJti;

    private Instant reuseDetectedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // Required by JPA.
    }

    RefreshToken(String jti, UUID userId, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    boolean belongsTo(UUID user) {
        return userId.equals(user);
    }

    boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** Revoked because a newer token replaced it: using it again means it was copied. */
    boolean wasRotated() {
        return revokedAt != null && replacedByJti != null;
    }

    void rotate(Instant now, String newJti) {
        this.revokedAt = now;
        this.replacedByJti = newJti;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    void markReuseDetected(Instant now) {
        this.reuseDetectedAt = now;
    }
}
