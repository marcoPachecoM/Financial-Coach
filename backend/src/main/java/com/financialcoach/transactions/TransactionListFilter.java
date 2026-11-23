package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;

/**
 * Optional filters of {@code GET /transactions}, on top of the account and the dates. Each null
 * field means "no filter".
 *
 * @param text          contained in the name, the category or the notes (case-insensitive)
 * @param categoryGroup exact category group
 * @param minAmount     lower bound of the absolute amount, inclusive
 * @param maxAmount     upper bound of the absolute amount, inclusive
 */
record TransactionListFilter(String text, CategoryGroup categoryGroup, BigDecimal minAmount, BigDecimal maxAmount) {

    static final TransactionListFilter NONE = new TransactionListFilter(null, null, null, null);
}
