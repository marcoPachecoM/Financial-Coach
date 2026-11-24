package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One transaction for other modules (the copilot). {@code amount} is positive for both types;
 * {@code tags} are sorted alphabetically.
 */
public record TransactionLine(LocalDate date, TransactionType type, String name, BigDecimal amount,
                              CategoryGroup categoryGroup, String category, List<String> tags) {

    static TransactionLine from(Transaction transaction) {
        return new TransactionLine(transaction.getOccurredAt().toLocalDate(), transaction.getType(),
                transaction.getName(), transaction.getAmount().abs(), transaction.getCategoryGroup(),
                transaction.getCategory(), transaction.tagNames());
    }
}
