package com.financialcoach.transactions;

import com.financialcoach.categorization.CategorizationService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Decides whether a real charge (from a CSV or typed by hand) is an occurrence of a rule:
 * same account and type, the same merchant, and a date close enough to a due date.
 */
final class RecurringMatcher {

    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]+");

    private RecurringMatcher() {
    }

    /**
     * The due date of the occurrence that a transaction with these values would cover, if it
     * matches the rule. {@code merchantKey} is {@link CategorizationService#merchantKey} of its name.
     */
    static Optional<LocalDate> dueDateFor(RecurringTransaction rule, UUID accountId, TransactionType type,
                                          String merchantKey, LocalDate date) {
        if (!rule.getAccountId().equals(accountId) || rule.getType() != type
                || !sameMerchant(CategorizationService.merchantKey(rule.getName()), merchantKey)) {
            return Optional.empty();
        }
        RecurrenceFrequency frequency = rule.getFrequency();
        LocalDate due = frequency.nearest(rule.getStartDate(), date);
        boolean closeEnough = Math.abs(ChronoUnit.DAYS.between(due, date)) <= frequency.toleranceDays();
        return closeEnough && rule.isWithinEnd(due) ? Optional.of(due) : Optional.empty();
    }

    static Optional<LocalDate> dueDateFor(RecurringTransaction rule, Transaction transaction) {
        return dueDateFor(rule, transaction.getAccountId(), transaction.getType(),
                CategorizationService.merchantKey(transaction.getName()), transaction.getOccurredAt().toLocalDate());
    }

    /**
     * Equal names, or one is the other plus more words: bank exports add suffixes, so
     * "NETFLIX.COM 866-579" (words "netflix com") matches a rule named "Netflix".
     */
    static boolean sameMerchant(String merchantKeyA, String merchantKeyB) {
        String a = words(merchantKeyA);
        String b = words(merchantKeyB);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b) || a.startsWith(b + " ") || b.startsWith(a + " ");
    }

    /** Merchant keys keep dots ("netflix.com"); for matching, only letters count. */
    private static String words(String merchantKey) {
        return NON_LETTERS.matcher(merchantKey).replaceAll(" ").strip();
    }
}
