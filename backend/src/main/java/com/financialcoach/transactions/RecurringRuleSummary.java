package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import java.math.BigDecimal;
import java.time.LocalDate;

/** An active recurring rule for other modules (the copilot). {@code amount} is positive. */
public record RecurringRuleSummary(String name, TransactionType type, BigDecimal amount, RecurrenceFrequency frequency,
                                   CategoryGroup categoryGroup, LocalDate nextDueDate) {

    static RecurringRuleSummary from(RecurringTransaction rule) {
        return new RecurringRuleSummary(rule.getName(), rule.getType(), rule.getAmount().abs(), rule.getFrequency(),
                rule.getCategoryGroup(), rule.getNextDueDate());
    }
}
