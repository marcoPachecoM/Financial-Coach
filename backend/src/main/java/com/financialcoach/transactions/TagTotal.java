package com.financialcoach.transactions;

import java.math.BigDecimal;

/** Expenses of one tag in a period. {@code amount} is positive. */
public record TagTotal(String name, long count, BigDecimal amount) {

    /** Constructor used by the JPQL query (count() returns a Long). */
    TagTotal(String name, Long count, BigDecimal amount) {
        this(name, count.longValue(), amount);
    }
}
