package com.financialcoach.transactions;

/** Channel that created a transaction ({@code transactions.source}). Internal, not exposed by the API. */
enum TransactionSource {
    MANUAL,
    CSV,
    /** Created automatically by a recurring transaction rule. */
    RECURRING
}
