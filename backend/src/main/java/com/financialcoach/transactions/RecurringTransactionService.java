package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ConflictException;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recurring transaction rules: creating them from a transaction, editing them, and creating the
 * occurrences that are due.
 *
 * <p>For each due occurrence the generator first looks for a real charge that already covers it
 * (e.g. imported from a CSV before the due date) and links it; only when there is none does it
 * create the transaction itself. That way an automatic occurrence and the bank's charge do not
 * end up as two expenses.
 */
@Service
class RecurringTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionService.class);

    /** Bounds one run for a rule whose start is far in the past; the next run continues. */
    static final int MAX_OCCURRENCES_PER_RUN = 120;

    private final RecurringTransactionRepository rules;
    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final TransactionTemplate perRule;
    private final Clock clock;

    RecurringTransactionService(RecurringTransactionRepository rules, TransactionRepository transactions,
                                AccountService accountService, PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.rules = rules;
        this.transactions = transactions;
        this.accountService = accountService;
        this.perRule = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Makes {@code first} occurrence 0 of a new rule and creates the occurrences already due
     * (when {@code first} is in the past). Runs in the caller's transaction.
     */
    @Transactional
    RecurringTransaction createFrom(Transaction first, RecurrenceFrequency frequency, LocalDate endDate) {
        if (first.getRecurringTransactionId() != null) {
            throw new ConflictException("ALREADY_RECURRING", "This transaction is already part of a recurring transaction.");
        }
        LocalDate start = first.getOccurredAt().toLocalDate();
        if (endDate != null && endDate.isBefore(start)) {
            throw new ValidationException("end_date", "must be on or after the date of the transaction");
        }
        RecurringTransaction rule = rules.saveAndFlush(RecurringTransaction.from(first, frequency, endDate));
        first.linkTo(rule, start);
        transactions.saveAndFlush(first);
        generate(rule, today());
        return rule;
    }

    @Transactional
    RecurringTransaction create(UUID userId, RecurringTransactionCreateRequest request) {
        Transaction transaction = transactions.findOwned(request.transactionId(), userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
        return createFrom(transaction, request.frequency(), request.endDate());
    }

    @Transactional(readOnly = true)
    List<RecurringTransaction> list(UUID userId, UUID accountId) {
        accountService.requireOwned(userId, accountId);
        return rules.findByAccount(userId, accountId);
    }

    @Transactional(readOnly = true)
    RecurringTransaction get(UUID userId, UUID ruleId) {
        return rules.findOwned(ruleId, userId)
                .orElseThrow(() -> new NotFoundException("Recurring transaction not found."));
    }

    /** Changes apply to the occurrences created from now on; existing transactions are kept. */
    @Transactional
    RecurringTransaction update(UUID userId, UUID ruleId, RecurringTransactionUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new ValidationException("At least one field must be provided.");
        }
        RecurringTransaction rule = get(userId, ruleId);

        if (request.nameProvided()) {
            rule.rename(required("name", request.name()).strip());
        }
        if (request.amountProvided()) {
            BigDecimal amount = Money.parse(required("amount", request.amount()));
            AmountRules.signViolation(rule.getType(), amount).ifPresent(message -> {
                throw new ValidationException("amount", message);
            });
            rule.changeAmount(amount);
        }
        if (request.categoryGroupProvided()) {
            rule.changeCategoryGroup(required("category_group", request.categoryGroup()));
        }
        if (request.categoryProvided()) {
            rule.changeCategory(blankToNull(request.category()));
        }
        if (request.notesProvided()) {
            rule.changeNotes(blankToNull(request.notes()));
        }
        if (request.endDateProvided()) {
            LocalDate endDate = request.endDate();
            if (endDate != null && endDate.isBefore(rule.getStartDate())) {
                throw new ValidationException("end_date", "must be on or after start_date");
            }
            rule.changeEndDate(endDate);
            // A rule that had ended starts again after today (skipped occurrences are not created).
            rule.resumeAfter(today());
        }
        return rules.saveAndFlush(rule);
    }

    /** Deletes the rule; its transactions are kept as ordinary ones. */
    @Transactional
    void delete(UUID userId, UUID ruleId) {
        RecurringTransaction rule = get(userId, ruleId);
        transactions.unlinkAll(rule.getId());
        rules.delete(rules.getReferenceById(rule.getId()));
        rules.flush();
    }

    /**
     * Creates the occurrences due on or before {@code today} for every rule. Each rule runs in
     * its own database transaction, so one failing rule does not stop the others. Returns how
     * many rules were processed.
     */
    int generateDue(LocalDate today) {
        int processed = 0;
        for (UUID ruleId : rules.findDueIds(today)) {
            try {
                Boolean done = perRule.execute(status -> rules.lockIfDue(ruleId, today)
                        .map(rule -> {
                            generate(rule, today);
                            return true;
                        })
                        .orElse(false));
                if (Boolean.TRUE.equals(done)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("Could not generate the occurrences of recurring transaction {}", ruleId, e);
            }
        }
        return processed;
    }

    /** Creates (or links) the occurrences of {@code rule} due on or before {@code today}. */
    private void generate(RecurringTransaction rule, LocalDate today) {
        int count = 0;
        while (rule.getNextDueDate() != null && !rule.getNextDueDate().isAfter(today)
                && count < MAX_OCCURRENCES_PER_RUN) {
            LocalDate due = rule.getNextDueDate();
            if (transactions.findOccurrence(rule.getId(), due).isEmpty()) {
                Optional<Transaction> realCharge = findRealCharge(rule, due);
                if (realCharge.isPresent()) {
                    realCharge.get().linkTo(rule, due);
                } else {
                    transactions.save(Transaction.generated(rule, due));
                }
            }
            rule.advance();
            count++;
        }
        rules.save(rule);
        transactions.flush();
    }

    /** An unlinked transaction of the same merchant close to {@code due}; the closest one wins. */
    private Optional<Transaction> findRealCharge(RecurringTransaction rule, LocalDate due) {
        int tolerance = rule.getFrequency().toleranceDays();
        return transactions.findUnlinked(rule.getUserId(), rule.getAccountId(), rule.getType(),
                        due.minusDays(tolerance).atStartOfDay(), due.plusDays(tolerance + 1L).atStartOfDay())
                .stream()
                .filter(candidate -> RecurringMatcher.dueDateFor(rule, candidate).filter(due::equals).isPresent())
                .min(Comparator.comparingLong(
                        candidate -> Math.abs(ChronoUnit.DAYS.between(due, candidate.getOccurredAt().toLocalDate()))));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new ValidationException(field, "must not be null");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
