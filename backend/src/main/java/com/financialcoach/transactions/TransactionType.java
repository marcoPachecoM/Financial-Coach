package com.financialcoach.transactions;

/**
 * Determines the amount sign: EXPENSE positive, INCOME negative (ck_transactions_amount_sign).
 * TRANSFER is intentionally left out for now.
 */
public enum TransactionType {
    EXPENSE,
    INCOME
}
