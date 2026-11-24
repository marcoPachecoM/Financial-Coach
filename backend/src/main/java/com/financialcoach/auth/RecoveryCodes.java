package com.financialcoach.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Format, generation and hashing of recovery codes. Codes look like {@code k7m2-9xqp-h3vt}:
 * 12 characters from an alphabet without look-alikes (no 0/o, 1/l/i), about 60 bits of entropy.
 */
final class RecoveryCodes {

    static final int COUNT = 8;
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 12;
    private static final int GROUP = 4;

    private RecoveryCodes() {
    }

    /** {@link #COUNT} distinct codes, formatted for display. */
    static List<String> generate(SecureRandom random) {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < COUNT) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < LENGTH; i++) {
                if (i > 0 && i % GROUP == 0) {
                    code.append('-');
                }
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return List.copyOf(codes);
    }

    /** What the user typed, as stored: case, dashes and spaces do not matter. */
    static String normalize(String typed) {
        return typed == null ? "" : typed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** SHA-256 (hex) of the normalized code. */
    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
