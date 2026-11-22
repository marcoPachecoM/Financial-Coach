package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * SHA-256 (hex) of (account, datetime, name, amount, occurrence) that identifies a CSV row, so
 * uploading the same file twice does not duplicate transactions.
 *
 * <p>{@code occurrence} counts identical rows inside one file (1, 2, ...): two equal coffees on
 * the same minute are two transactions, and re-uploading the file still matches both.
 *
 * <p><b>The format is stored in the database: changing it makes every earlier import look new.</b>
 */
final class TransactionFingerprint {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    /** ASCII "unit separator": cannot appear in a CSV value, so fields never run together. */
    private static final char SEPARATOR = '\u001F';

    private TransactionFingerprint() {
    }

    /** The identity of a row, without the occurrence. Equal keys within a file get occurrences 1, 2... */
    static String key(UUID accountId, LocalDateTime occurredAt, String name, BigDecimal amount) {
        return accountId.toString() + SEPARATOR + DATETIME.format(occurredAt) + SEPARATOR
                + name + SEPARATOR + amount.toPlainString();
    }

    static String of(String key, int occurrence) {
        byte[] input = (key + SEPARATOR + occurrence).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256().digest(input));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
