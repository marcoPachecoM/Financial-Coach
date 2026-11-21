package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What {@link RecurringReport} found for an account and month. Amounts follow the API sign
 * rule (EXPENSE positive, INCOME negative).
 *
 * @param upcoming     occurrences due in the next days (only for the current month)
 * @param missing      occurrences created automatically that the bank statements do not show
 * @param priceChanges real charges whose amount differs from the rule's amount
 * @param audited      recurring expenses charged this month at the expected amount, one per rule
 * @param suggestions  series of charges that look recurring but have no rule
 */
public record RecurringOverview(
        List<UpcomingCharge> upcoming,
        List<MissingCharge> missing,
        List<PriceChange> priceChanges,
        List<AuditedCharge> audited,
        List<Suggestion> suggestions) {

    public record UpcomingCharge(UUID recurringTransactionId, String name, BigDecimal amount, LocalDate dueDate) {
    }

    public record MissingCharge(UUID transactionId, UUID recurringTransactionId, String name, BigDecimal amount,
                                LocalDate dueDate) {
    }

    public record PriceChange(UUID recurringTransactionId, UUID transactionId, String name,
                              BigDecimal expectedAmount, BigDecimal chargedAmount, LocalDate dueDate) {
    }

    public record AuditedCharge(UUID recurringTransactionId, String name, BigDecimal amount) {
    }

    /** {@code transactionId} is the latest charge: making it recurring continues the series. */
    public record Suggestion(UUID transactionId, String name, BigDecimal amount, RecurrenceFrequency frequency,
                             int occurrences) {
    }
}
