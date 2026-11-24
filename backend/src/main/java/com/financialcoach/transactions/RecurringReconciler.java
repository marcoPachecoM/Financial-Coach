package com.financialcoach.transactions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Links imported charges to the recurring transactions they are occurrences of.
 *
 * <p>When an occurrence was already created automatically, the imported charge replaces it:
 * the generated transaction is soft-deleted and the real one (with the real amount) takes its
 * place. When the occurrence is still in the future, the charge is linked to it and the
 * generator will not create it. A charge whose occurrence already has a real transaction stays
 * unlinked.
 */
@Component
class RecurringReconciler {

    private final RecurringTransactionRepository rules;
    private final TransactionRepository transactions;

    RecurringReconciler(RecurringTransactionRepository rules, TransactionRepository transactions) {
        this.rules = rules;
        this.transactions = transactions;
    }

    /**
     * Links {@code newTransactions} (not saved yet) and soft-deletes the generated ones they
     * replace. Those deletions are flushed here: Hibernate runs inserts before updates, so they
     * must reach the database before the new rows, or ux_transactions_recurrence would reject them.
     * Returns how many new transactions were linked.
     */
    int reconcile(UUID userId, Collection<UUID> accountIds, List<Transaction> newTransactions, Instant now) {
        if (newTransactions.isEmpty()) {
            return 0;
        }
        List<RecurringTransaction> accountRules = rules.findByAccounts(userId, accountIds);
        if (accountRules.isEmpty()) {
            return 0;
        }

        Map<Occurrence, Transaction> covered = coveredOccurrences(accountRules, newTransactions);
        int linked = 0;
        for (Transaction transaction : newTransactions) {
            for (RecurringTransaction rule : accountRules) {
                Optional<LocalDate> due = RecurringMatcher.dueDateFor(rule, transaction);
                if (due.isEmpty()) {
                    continue;
                }
                Occurrence occurrence = new Occurrence(rule.getId(), due.get());
                Transaction existing = covered.get(occurrence);
                if (existing == null || existing.isGenerated()) {
                    if (existing != null) {
                        existing.markDeleted(now);
                        // The real charge keeps the tags the generated occurrence had.
                        transaction.addTags(existing.getTags());
                    }
                    transaction.linkTo(rule, due.get());
                    covered.put(occurrence, transaction);
                    linked++;
                }
                break; // One rule per charge.
            }
        }
        transactions.flush();
        return linked;
    }

    /** The active transactions that already cover an occurrence near the dates of the new rows. */
    private Map<Occurrence, Transaction> coveredOccurrences(List<RecurringTransaction> accountRules,
                                                            List<Transaction> newTransactions) {
        LocalDate first = newTransactions.getFirst().getOccurredAt().toLocalDate();
        LocalDate last = first;
        for (Transaction transaction : newTransactions) {
            LocalDate date = transaction.getOccurredAt().toLocalDate();
            first = date.isBefore(first) ? date : first;
            last = date.isAfter(last) ? date : last;
        }
        // Wider than any tolerance, so every occurrence a row can match is included.
        int margin = RecurrenceFrequency.YEARLY.toleranceDays() + 1;
        List<UUID> ruleIds = accountRules.stream().map(RecurringTransaction::getId).toList();

        Map<Occurrence, Transaction> covered = new HashMap<>();
        for (Transaction transaction : transactions.findOccurrences(ruleIds, first.minusDays(margin), last.plusDays(margin))) {
            covered.put(new Occurrence(transaction.getRecurringTransactionId(), transaction.getRecurrenceDate()),
                    transaction);
        }
        return covered;
    }

    private record Occurrence(UUID ruleId, LocalDate dueDate) {
    }
}
