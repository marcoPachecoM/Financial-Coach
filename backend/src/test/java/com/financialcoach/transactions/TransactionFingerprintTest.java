package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionFingerprintTest {

    private static final UUID ACCOUNT = UUID.fromString("019a0000-0000-7000-8000-000000000101");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 2, 13, 35);

    /**
     * Fingerprints are stored. If this value changes, files imported before the change are no
     * longer recognized as duplicates. Only update it together with a data migration.
     */
    @Test
    void formatIsStable() {
        String key = TransactionFingerprint.key(ACCOUNT, AT, "La tiendita", new BigDecimal("100.00"));

        assertThat(TransactionFingerprint.of(key, 1))
                .isEqualTo("85a797ee45f039ce19aac95858ff8bb7ef1f1d14b38a594b4605e790ce95a3e7");
    }

    @Test
    void occurrenceDistinguishesIdenticalRows() {
        String key = TransactionFingerprint.key(ACCOUNT, AT, "Coffee", new BigDecimal("45.00"));

        assertThat(TransactionFingerprint.of(key, 1)).isNotEqualTo(TransactionFingerprint.of(key, 2));
    }

    @Test
    void secondsAreAlwaysPartOfTheKey() {
        // LocalDateTime.toString() would drop ":00"; the key must not depend on that.
        assertThat(TransactionFingerprint.key(ACCOUNT, AT, "x", BigDecimal.ONE)).contains("2026-01-02T13:35:00");
    }
}
