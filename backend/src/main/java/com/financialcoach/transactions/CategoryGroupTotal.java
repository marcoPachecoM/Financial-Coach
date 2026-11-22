package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;

/** Total of one category group in a period. {@code amount} is positive for both types. */
public record CategoryGroupTotal(CategoryGroup categoryGroup, long count, BigDecimal amount) {

    /** Constructor used by the JPQL query (count() returns a Long). */
    CategoryGroupTotal(CategoryGroup categoryGroup, Long count, BigDecimal amount) {
        this(categoryGroup, count.longValue(), amount);
    }
}
