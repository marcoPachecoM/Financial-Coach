package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The sign rule of the ck_transactions_amount_sign constraint (EXPENSE positive, INCOME negative,
 * never zero), checked in Java so it is reported as a 400 instead of a database error.
 * Shared by the form and the CSV import.
 */
final class AmountRules {

    private AmountRules() {
    }

    /** The error message if the amount breaks the rule for this type. */
    static Optional<String> signViolation(TransactionType type, BigDecimal amount) {
        if (amount.signum() == 0) {
            return Optional.of("must not be zero");
        }
        if (type == TransactionType.EXPENSE && amount.signum() < 0) {
            return Optional.of("EXPENSE amounts must be positive.");
        }
        if (type == TransactionType.INCOME && amount.signum() > 0) {
            return Optional.of("INCOME amounts must be negative.");
        }
        return Optional.empty();
    }
}
