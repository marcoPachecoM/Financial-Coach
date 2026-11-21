package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.categorization.CategorizationService;
import com.financialcoach.categorization.CategoryGroupAssignment;
import com.financialcoach.categorization.CategoryGroupSource;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction use cases for the form (and later WhatsApp). The CSV import lives in
 * CsvImportService and shares the same rules (AmountRules, categorization, ownership).
 */
@Service
public class TransactionService {

    /** Newest first; id breaks ties so pages are stable when several share a datetime. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final CategorizationService categorizationService;

    TransactionService(TransactionRepository transactions, AccountService accountService,
                       CategorizationService categorizationService) {
        this.transactions = transactions;
        this.accountService = accountService;
        this.categorizationService = categorizationService;
    }

    @Transactional(readOnly = true)
    Page<Transaction> list(UUID userId, UUID accountId, int page, int size, DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.findPage(userId, accountId, range.from(), range.to(),
                PageRequest.of(page - 1, size, NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    Transaction get(UUID userId, UUID transactionId) {
        return transactions.findOwned(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
    }

    @Transactional
    Transaction create(UUID userId, TransactionCreateRequest request) {
        accountService.requireOwned(userId, request.accountId());

        BigDecimal amount = Money.parse(request.amount());
        checkAmountSign(request.type(), amount);

        String name = request.name().strip();
        CategoryGroupAssignment group = request.categoryGroup() != null
                ? new CategoryGroupAssignment(request.categoryGroup(), CategoryGroupSource.USER)
                : categorizationService.categorize(name);

        Transaction transaction = Transaction.manual(
                userId,
                request.accountId(),
                request.type(),
                truncateToSeconds(request.datetime()),
                name,
                amount,
                group.group(),
                group.source(),
                blankToNull(request.category()),
                blankToNull(request.notes()));
        return transactions.saveAndFlush(transaction);
    }

    @Transactional
    Transaction update(UUID userId, UUID transactionId, TransactionUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new ValidationException("At least one field must be provided.");
        }
        Transaction transaction = get(userId, transactionId);

        // Queries first, changes after: no query may run while the entity is half-updated,
        // because Hibernate could flush an intermediate state that violates a constraint.
        if (request.accountIdProvided()) {
            accountService.requireOwned(userId, required("account_id", request.accountId()));
        }

        if (request.typeProvided()) {
            transaction.changeType(required("type", request.type()));
        }
        if (request.amountProvided()) {
            transaction.changeAmount(Money.parse(required("amount", request.amount())));
        }
        if (request.datetimeProvided()) {
            transaction.changeOccurredAt(truncateToSeconds(required("datetime", request.datetime())));
        }
        if (request.nameProvided()) {
            transaction.rename(required("name", request.name()).strip());
        }
        if (request.categoryGroupProvided()) {
            transaction.changeCategoryGroupByUser(required("category_group", request.categoryGroup()));
        }
        if (request.categoryProvided()) {
            transaction.changeCategory(blankToNull(request.category()));
        }
        if (request.notesProvided()) {
            transaction.changeNotes(blankToNull(request.notes()));
        }
        if (request.accountIdProvided()) {
            transaction.moveTo(request.accountId());
        }

        // Checked on the final state: changing only the type can break the sign rule.
        checkAmountSign(transaction.getType(), transaction.getAmount());
        return transactions.saveAndFlush(transaction);
    }

    @Transactional
    void delete(UUID userId, UUID transactionId) {
        Transaction transaction = get(userId, transactionId);
        transaction.markDeleted(Instant.now());
        transactions.saveAndFlush(transaction);
    }

    private static void checkAmountSign(TransactionType type, BigDecimal amount) {
        AmountRules.signViolation(type, amount).ifPresent(message -> {
            throw new ValidationException("amount", message);
        });
    }

    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new ValidationException(field, "must not be null");
        }
        return value;
    }

    /** The contract format has no fractional seconds. */
    private static LocalDateTime truncateToSeconds(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
