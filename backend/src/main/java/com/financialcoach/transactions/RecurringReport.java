package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.categorization.CategorizationService;
import com.financialcoach.shared.DateRange;
import com.financialcoach.transactions.RecurringOverview.AuditedCharge;
import com.financialcoach.transactions.RecurringOverview.MissingCharge;
import com.financialcoach.transactions.RecurringOverview.PriceChange;
import com.financialcoach.transactions.RecurringOverview.Suggestion;
import com.financialcoach.transactions.RecurringOverview.UpcomingCharge;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facts about an account's recurring transactions for one month, for other modules (the alerts,
 * later the copilot). Fails with 404 unless the account belongs to the user.
 */
@Service
public class RecurringReport {

    /** How far ahead an occurrence counts as upcoming. */
    static final int UPCOMING_DAYS = 7;

    /** How far back the detector looks for series of charges. */
    static final int DETECTION_WINDOW_DAYS = 120;

    private final RecurringTransactionRepository rules;
    private final TransactionRepository transactions;
    private final AccountService accountService;

    RecurringReport(RecurringTransactionRepository rules, TransactionRepository transactions,
                    AccountService accountService) {
        this.rules = rules;
        this.transactions = transactions;
        this.accountService = accountService;
    }

    /** Rules that still create occurrences (not ended or stopped), in name order. */
    @Transactional(readOnly = true)
    public List<RecurringRuleSummary> activeRules(UUID userId, UUID accountId) {
        accountService.requireOwned(userId, accountId);
        return rules.findByAccount(userId, accountId).stream()
                .filter(rule -> rule.getNextDueDate() != null)
                .map(RecurringRuleSummary::from)
                .toList();
    }

    /**
     * Recurring expenses not created yet that fall due on or before {@code through}, earliest
     * first, with positive amounts. Used to project what is still to be charged this month.
     */
    @Transactional(readOnly = true)
    public List<UpcomingCharge> pendingExpenses(UUID userId, UUID accountId, LocalDate through) {
        accountService.requireOwned(userId, accountId);
        return rules.findByAccount(userId, accountId).stream()
                .filter(rule -> rule.getType() == TransactionType.EXPENSE)
                .flatMap(rule -> rule.pendingDueDatesThrough(through).stream()
                        .map(date -> new UpcomingCharge(rule.getId(), rule.getName(), rule.getAmount().abs(), date)))
                .sorted(Comparator.comparing(UpcomingCharge::dueDate).thenComparing(UpcomingCharge::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringOverview report(UUID userId, UUID accountId, YearMonth month, LocalDate today) {
        accountService.requireOwned(userId, accountId);
        List<RecurringTransaction> accountRules = rules.findByAccount(userId, accountId);
        Map<UUID, RecurringTransaction> rulesById = accountRules.stream()
                .collect(Collectors.toMap(RecurringTransaction::getId, Function.identity()));

        DateRange range = DateRange.of(month);
        List<Transaction> linked = transactions.findLinked(userId, accountId, range.from(), range.to());
        Optional<LocalDate> importedUntil = transactions.findLatestImported(userId, accountId)
                .map(LocalDateTime::toLocalDate);

        List<MissingCharge> missing = new ArrayList<>();
        List<PriceChange> priceChanges = new ArrayList<>();
        List<AuditedCharge> audited = new ArrayList<>();
        Set<UUID> auditedRules = new HashSet<>();

        for (Transaction transaction : linked) {
            RecurringTransaction rule = rulesById.get(transaction.getRecurringTransactionId());
            boolean expectedAmount = transaction.getAmount().compareTo(rule.getAmount()) == 0;

            boolean isMissing = transaction.isGenerated() && isMissingFromStatements(transaction, rule, importedUntil);

            if (isMissing) {
                missing.add(new MissingCharge(transaction.getId(), rule.getId(), transaction.getName(),
                        transaction.getAmount(), transaction.getRecurrenceDate()));
            }
            // Only real charges: a generated one differs only if the rule's amount changed later.
            if (!transaction.isGenerated() && !expectedAmount) {
                priceChanges.add(new PriceChange(rule.getId(), transaction.getId(), rule.getName(),
                        rule.getAmount(), transaction.getAmount(), transaction.getRecurrenceDate()));
            }
            if (transaction.getType() == TransactionType.EXPENSE && expectedAmount && !isMissing
                    && auditedRules.add(rule.getId())) {
                audited.add(new AuditedCharge(rule.getId(), rule.getName(), rule.getAmount()));
            }
        }

        return new RecurringOverview(
                upcoming(accountRules, month, today),
                missing,
                priceChanges,
                audited,
                suggestions(userId, accountId, accountRules, month, today));
    }

    /**
     * The bank statements already cover the due date (an imported transaction is dated after it,
     * beyond the tolerance) but no real charge replaced the generated one: maybe it was cancelled.
     */
    private static boolean isMissingFromStatements(Transaction generated, RecurringTransaction rule,
                                                   Optional<LocalDate> importedUntil) {
        LocalDate lastDayToAppear = generated.getRecurrenceDate().plusDays(rule.getFrequency().toleranceDays());
        return importedUntil.filter(until -> until.isAfter(lastDayToAppear)).isPresent();
    }

    /** Only for the current month: the next occurrences of the following days. */
    private List<UpcomingCharge> upcoming(List<RecurringTransaction> accountRules, YearMonth month, LocalDate today) {
        if (!YearMonth.from(today).equals(month)) {
            return List.of();
        }
        LocalDate until = today.plusDays(UPCOMING_DAYS);
        return accountRules.stream()
                .filter(rule -> rule.getNextDueDate() != null)
                .filter(rule -> !rule.getNextDueDate().isBefore(today) && !rule.getNextDueDate().isAfter(until))
                // Already covered by a charge imported before its due date.
                .filter(rule -> transactions.findOccurrence(rule.getId(), rule.getNextDueDate()).isEmpty())
                .sorted(Comparator.comparing(RecurringTransaction::getNextDueDate)
                        .thenComparing(RecurringTransaction::getName))
                .map(rule -> new UpcomingCharge(rule.getId(), rule.getName(), rule.getAmount(), rule.getNextDueDate()))
                .toList();
    }

    private List<Suggestion> suggestions(UUID userId, UUID accountId, List<RecurringTransaction> accountRules,
                                         YearMonth month, LocalDate today) {
        LocalDate asOf = month.atEndOfMonth().isBefore(today) ? month.atEndOfMonth() : today;
        List<String> activeRuleKeys = accountRules.stream()
                .filter(rule -> rule.getNextDueDate() != null)
                .map(rule -> CategorizationService.merchantKey(rule.getName()))
                .toList();

        List<RecurringDetector.Charge> charges = new ArrayList<>();
        Set<String> excludedKeys = new HashSet<>();
        for (Transaction transaction : transactions.findUnlinked(userId, accountId, TransactionType.EXPENSE,
                asOf.minusDays(DETECTION_WINDOW_DAYS).atStartOfDay(), asOf.plusDays(1).atStartOfDay())) {
            String key = CategorizationService.merchantKey(transaction.getName());
            if (activeRuleKeys.stream().anyMatch(ruleKey -> RecurringMatcher.sameMerchant(ruleKey, key))) {
                excludedKeys.add(key);
            }
            charges.add(new RecurringDetector.Charge(transaction.getId(), transaction.getName(), key,
                    transaction.getOccurredAt().toLocalDate(), transaction.getAmount()));
        }

        return RecurringDetector.detect(charges, excludedKeys, asOf).stream()
                .map(series -> new Suggestion(series.latest().transactionId(), series.latest().name(),
                        series.latest().amount(), series.frequency(), series.occurrences()))
                .toList();
    }
}
