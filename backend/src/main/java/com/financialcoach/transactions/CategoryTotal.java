package com.financialcoach.transactions;

import com.financialcoach.categorization.Category;
import java.math.BigDecimal;

/** Total of one category in a period. {@code amount} is positive for both types. */
public record CategoryTotal(Category category, long count, BigDecimal amount) {

    /** Constructor used by the JPQL query (count() returns a Long). */
    CategoryTotal(Category category, Long count, BigDecimal amount) {
        this(category, count.longValue(), amount);
    }
}
