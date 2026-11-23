package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** What makes two transactions "the same" on import: account, date and time, name and amount. */
record TransactionIdentity(UUID accountId, LocalDateTime occurredAt, String name, BigDecimal amount) {

    /** Same key the CSV fingerprint is built from, so rows and transactions compare directly. */
    String key() {
        return TransactionFingerprint.key(accountId, occurredAt, name, amount);
    }
}
