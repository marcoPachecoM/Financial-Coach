package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finds series of charges that look like a subscription: the same merchant at least 3 times,
 * at a regular interval, for about the same amount. Pure logic (no database), so it is unit tested.
 */
final class RecurringDetector {

    static final int MIN_OCCURRENCES = 3;

    /** Amounts may vary this much from the median (taxes, exchange rates, small price changes). */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.10");

    /** Accepted days between two charges, per frequency. Yearly cannot be seen in a few months. */
    private static final List<Band> BANDS = List.of(
            new Band(RecurrenceFrequency.WEEKLY, 6, 8),
            new Band(RecurrenceFrequency.BIWEEKLY, 13, 16),
            new Band(RecurrenceFrequency.MONTHLY, 26, 35));

    private RecurringDetector() {
    }

    /** One past charge; {@code merchantKey} is the normalized name used to group them. */
    record Charge(UUID transactionId, String name, String merchantKey, LocalDate date, BigDecimal amount) {
    }

    /** {@code latest} is the most recent charge of the series. */
    record Series(Charge latest, RecurrenceFrequency frequency, int occurrences) {
    }

    /**
     * @param charges        candidate charges (not linked to any rule), any order
     * @param excludedKeys   merchants that already have an active rule
     * @param asOf           end of the period: a series whose last charge is older than one
     *                       interval (plus tolerance) has stopped and is not suggested
     */
    static List<Series> detect(List<Charge> charges, Set<String> excludedKeys, LocalDate asOf) {
        Map<String, List<Charge>> byMerchant = new LinkedHashMap<>();
        for (Charge charge : charges) {
            if (!charge.merchantKey().isEmpty() && !excludedKeys.contains(charge.merchantKey())) {
                byMerchant.computeIfAbsent(charge.merchantKey(), key -> new ArrayList<>()).add(charge);
            }
        }

        List<Series> found = new ArrayList<>();
        for (List<Charge> series : byMerchant.values()) {
            if (series.size() < MIN_OCCURRENCES) {
                continue;
            }
            series.sort(Comparator.comparing(Charge::date).thenComparing(Charge::transactionId));
            Charge latest = series.getLast();
            frequencyOf(series)
                    .filter(band -> !isStale(latest.date(), band, asOf))
                    .filter(band -> similarAmounts(series))
                    .ifPresent(band -> found.add(new Series(latest, band.frequency(), series.size())));
        }
        found.sort(Comparator.comparing((Series s) -> s.latest().date()).reversed()
                .thenComparing(s -> s.latest().merchantKey()));
        return found;
    }

    /** The band that contains every gap between consecutive charges, if any. */
    private static Optional<Band> frequencyOf(List<Charge> series) {
        for (Band band : BANDS) {
            boolean allGapsFit = true;
            for (int i = 1; i < series.size() && allGapsFit; i++) {
                long gap = ChronoUnit.DAYS.between(series.get(i - 1).date(), series.get(i).date());
                allGapsFit = gap >= band.minDays() && gap <= band.maxDays();
            }
            if (allGapsFit) {
                return Optional.of(band);
            }
        }
        return Optional.empty();
    }

    private static boolean isStale(LocalDate latest, Band band, LocalDate asOf) {
        long sinceLatest = ChronoUnit.DAYS.between(latest, asOf);
        return sinceLatest > band.maxDays() + band.frequency().toleranceDays();
    }

    private static boolean similarAmounts(List<Charge> series) {
        List<BigDecimal> amounts = series.stream().map(c -> c.amount().abs()).sorted().toList();
        BigDecimal median = amounts.size() % 2 == 1
                ? amounts.get(amounts.size() / 2)
                : amounts.get(amounts.size() / 2 - 1).add(amounts.get(amounts.size() / 2))
                        .divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        BigDecimal allowed = median.multiply(AMOUNT_TOLERANCE);
        return amounts.stream().allMatch(amount -> amount.subtract(median).abs().compareTo(allowed) <= 0);
    }

    private record Band(RecurrenceFrequency frequency, int minDays, int maxDays) {
    }
}
