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

/** Maps {@code merchant_category_cache} (V6, column renamed in V7): normalized merchant name → category group. */
@Entity
@Table(name = "merchant_category_cache")
class MerchantCategory {

    @Id
    @Column(length = 255)
    private String merchantName;

    @Column(nullable = false, length = 50)
    private CategoryGroup categoryGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryGroupSource source;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected MerchantCategory() {
        // Required by JPA.
    }

    String getMerchantName() {
        return merchantName;
    }

    CategoryGroup getCategoryGroup() {
        return categoryGroup;
    }

    CategoryGroupSource getSource() {
        return source;
    }
}
