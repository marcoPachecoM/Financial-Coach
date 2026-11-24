package com.financialcoach.analytics;

import com.financialcoach.categorization.CategoryGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Maps the {@code category_budgets} table (V10): the monthly limit of one group of an account. */
@Entity
@Table(name = "category_budgets")
class CategoryBudget {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private CategoryGroup categoryGroup;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyAmount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CategoryBudget() {
    }

    CategoryBudget(UUID accountId, CategoryGroup categoryGroup, BigDecimal monthlyAmount) {
        this.accountId = accountId;
        this.categoryGroup = categoryGroup;
        this.monthlyAmount = monthlyAmount;
    }

    CategoryGroup getCategoryGroup() {
        return categoryGroup;
    }

    BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }
}
