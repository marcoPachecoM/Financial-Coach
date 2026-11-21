package com.financialcoach.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is exchanged as strings with up to 2 decimals (see {@code Money} / {@code PositiveMoney}
 * in docs/openapi.yaml) and stored as {@code numeric(14,2)}: at most 12 integer digits.
 */
public final class Money {

    /** Non-negative amount: "5000", "5000.5", "5000.50". */
    public static final String POSITIVE_PATTERN = "^\\d{1,12}(\\.\\d{1,2})?$";

    public static final String POSITIVE_MESSAGE = "must be a non-negative amount with up to 12 digits and 2 decimals";

    /** Signed amount: "100", "-20000.50". */
    public static final String SIGNED_PATTERN = "^-?\\d{1,12}(\\.\\d{1,2})?$";

    public static final String SIGNED_MESSAGE = "must be an amount with up to 12 digits and 2 decimals";

    private Money() {
    }

    /** Parses a value that already matched a money pattern. Always returns scale 2. */
    public static BigDecimal parse(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Formats with exactly 2 decimals and no exponent ("5000.00"). Null stays null. */
    public static String format(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
