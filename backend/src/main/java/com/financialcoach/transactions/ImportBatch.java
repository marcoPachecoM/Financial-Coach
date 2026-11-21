package com.financialcoach.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps the {@code import_batches} table (V4 migration): one row per successful CSV upload.
 * Imported transactions point to it, so a whole import can be audited or undone later.
 */
@Entity
@Table(name = "import_batches")
class ImportBatch {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 255, updatable = false)
    private String filename;

    @Column(nullable = false)
    private int recordsImported;

    @Column(nullable = false)
    private int recordsDuplicated;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportBatch() {
        // Required by JPA.
    }

    ImportBatch(UUID userId, String filename) {
        this.userId = userId;
        this.filename = filename;
    }

    void recordCounts(int imported, int duplicated) {
        this.recordsImported = imported;
        this.recordsDuplicated = duplicated;
    }

    UUID getId() {
        return id;
    }
}
