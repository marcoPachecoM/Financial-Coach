package com.financialcoach.auth;

import com.financialcoach.shared.error.ValidationException;
import java.util.Locale;

/**
 * Rules for a new password (sign-up, reset, change). Length matters most: at least 10
 * characters, at most 128 (so Argon2 never hashes huge inputs), and not the email or username.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** Fails with 400 VALIDATION_ERROR on {@code field}. {@code email} and {@code username} may be null. */
    static void check(String field, String password, String email, String username) {
        String problem = problem(password, email, username);
        if (problem != null) {
            throw new ValidationException(field, problem);
        }
    }

    private static String problem(String password, String email, String username) {
        if (password == null || password.isBlank()) {
            return "must not be blank";
        }
        if (password.length() < MIN_LENGTH) {
            return "must have at least %d characters".formatted(MIN_LENGTH);
        }
        if (password.length() > MAX_LENGTH) {
            return "must have at most %d characters".formatted(MAX_LENGTH);
        }
        String lower = password.strip().toLowerCase(Locale.ROOT);
        if (email != null && (lower.equals(email.toLowerCase(Locale.ROOT))
                || lower.equals(email.toLowerCase(Locale.ROOT).split("@")[0]))) {
            return "must not be your email";
        }
        if (username != null && lower.equals(username.toLowerCase(Locale.ROOT))) {
            return "must not be your username";
        }
        return null;
    }
}
