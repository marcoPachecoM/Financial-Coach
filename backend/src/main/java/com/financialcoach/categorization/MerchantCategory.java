package com.financialcoach.categorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Maps {@code merchant_category_cache} (V6 migration): normalized merchant name → category. */
@Entity
@Table(name = "merchant_category_cache")
class MerchantCategory {

    @Id
    @Column(length = 255)
    private String merchantName;

    @Column(nullable = false, length = 50)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategorySource source;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected MerchantCategory() {
        // Required by JPA.
    }

    Category getCategory() {
        return category;
    }

    CategorySource getSource() {
        return source;
    }
}
