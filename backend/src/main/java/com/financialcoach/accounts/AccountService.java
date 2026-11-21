package com.financialcoach.accounts;

import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ConflictException;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.shared.error.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account use cases. The CRUD methods are package-private (used by AccountController only);
 * public methods will be added for other modules, e.g. to check that an account belongs to a user.
 */
@Service
public class AccountService {

    private static final String DUPLICATE_NAME = "An account with this name already exists.";
    private static final String UNIQUE_NAME_CONSTRAINT = "ux_accounts_user_name";

    private final AccountRepository accounts;

    AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * For other modules: fails with 404 unless the account exists and belongs to the user.
     * Same response for "does not exist" and "belongs to someone else".
     */
    @Transactional(readOnly = true)
    public void requireOwned(UUID userId, UUID accountId) {
        get(userId, accountId);
    }

    @Transactional(readOnly = true)
    List<Account> list(UUID userId) {
        return accounts.findAllOwnedBy(userId);
    }

    @Transactional(readOnly = true)
    Account get(UUID userId, UUID accountId) {
        return accounts.findOwnedBy(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Account not found."));
    }

    @Transactional
    Account create(UUID userId, AccountCreateRequest request) {
        String name = request.name().strip();
        if (accounts.existsByName(userId, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        BigDecimal budget = request.monthlyBudget() == null ? null : Money.parse(request.monthlyBudget());
        return saveCheckingNameConflict(new Account(userId, name, budget));
    }

    @Transactional
    Account update(UUID userId, UUID accountId, AccountUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new ValidationException("At least one field must be provided.");
        }
        Account account = get(userId, accountId);

        if (request.nameProvided()) {
            if (request.name() == null) {
                throw new ValidationException("name", "must not be null");
            }
            String name = request.name().strip();
            if (accounts.existsByNameExcluding(userId, name, accountId)) {
                throw new ConflictException(DUPLICATE_NAME);
            }
            account.rename(name);
        }
        if (request.monthlyBudgetProvided()) {
            account.changeMonthlyBudget(
                    request.monthlyBudget() == null ? null : Money.parse(request.monthlyBudget()));
        }
        return saveCheckingNameConflict(account);
    }

    /**
     * The existsByName checks give a clean 409 in the normal case. The unique index is the real
     * guarantee: if two requests race, the database rejects the second one and it becomes a 409 too.
     * Flushing here also sets updated_at, so the response contains the new value.
     */
    private Account saveCheckingNameConflict(Account account) {
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(UNIQUE_NAME_CONSTRAINT)) {
                throw new ConflictException(DUPLICATE_NAME);
            }
            throw e;
        }
    }
}
