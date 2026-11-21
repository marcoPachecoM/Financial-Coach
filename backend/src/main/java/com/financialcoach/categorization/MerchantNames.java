package com.financialcoach.categorization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes merchant names so variants share one cache entry:
 * "  OXXO #1234 - Centro " and "oxxo centro" both become "oxxo centro".
 * Port of normalize_provider() from the Flask backend.
 */
final class MerchantNames {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SEPARATORS = Pattern.compile("[*#/_-]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MerchantNames() {
    }

    static String normalize(String name) {
        String value = Normalizer.normalize(name, Normalizer.Form.NFKD);
        value = DIACRITICS.matcher(value).replaceAll("");
        value = NON_ASCII.matcher(value).replaceAll("");
        value = value.toLowerCase(Locale.ROOT);
        value = DIGITS.matcher(value).replaceAll(" ");
        value = SEPARATORS.matcher(value).replaceAll(" ");
        value = WHITESPACE.matcher(value).replaceAll(" ");
        return value.strip();
    }
}
