package com.financialcoach.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** {@code Percentage} of openapi.yaml: 2 decimals, half-up ("22.62"). */
final class Percentages {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percentages() {
    }

    /** {@code part} as a percentage of {@code whole}; {@code whole} must not be zero. */
    static BigDecimal of(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
