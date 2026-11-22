package com.financialcoach.transactions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How often a recurring transaction repeats ({@code recurring_transactions.frequency}).
 *
 * <p>Every due date is computed from the rule's start date ({@code dueDate(start, n)}), never by
 * adding one period to the previous date: a rule that starts on January 31 is due on February 28
 * and then on March 31, without drifting to the 28th.
 */
public enum RecurrenceFrequency {

    WEEKLY(2),
    BIWEEKLY(3),
    MONTHLY(5),
    YEARLY(10);

    /** Occurrences are counted from 0 (the start date); this bounds the searches below. */
    private static final int MAX_OCCURRENCES = 100_000;

    private final int toleranceDays;

    RecurrenceFrequency(int toleranceDays) {
        this.toleranceDays = toleranceDays;
    }

    /**
     * How many days a real charge may be away from its due date and still count as that
     * occurrence (banks post card charges a few days late).
     */
    public int toleranceDays() {
        return toleranceDays;
    }

    /** Due date of occurrence {@code n}; occurrence 0 is the start date. */
    public LocalDate dueDate(LocalDate start, int n) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(n);
            case BIWEEKLY -> start.plusWeeks(2L * n);
            case MONTHLY -> start.plusMonths(n);
            case YEARLY -> start.plusYears(n);
        };
    }

    /** The first due date strictly after {@code date}. */
    public LocalDate nextAfter(LocalDate start, LocalDate date) {
        int n = Math.max(0, estimate(start, date));
        while (n > 0 && dueDate(start, n - 1).isAfter(date)) {
            n--;
        }
        while (!dueDate(start, n).isAfter(date)) {
            n = next(n);
        }
        return dueDate(start, n);
    }

    /**
     * The due date closest to {@code date} (occurrence 0 or later). On a tie, the earlier one.
     */
    public LocalDate nearest(LocalDate start, LocalDate date) {
        if (!date.isAfter(start)) {
            return start;
        }
        LocalDate after = nextAfter(start, date);
        LocalDate onOrBefore = previousOnOrBefore(start, date);
        long daysBefore = ChronoUnit.DAYS.between(onOrBefore, date);
        long daysAfter = ChronoUnit.DAYS.between(date, after);
        return daysBefore <= daysAfter ? onOrBefore : after;
    }

    /** The last due date on or before {@code date}; {@code date} must not be before the start. */
    private LocalDate previousOnOrBefore(LocalDate start, LocalDate date) {
        int n = Math.max(0, estimate(start, date));
        while (dueDate(start, n).isAfter(date)) {
            n--;
        }
        while (!dueDate(start, next(n)).isAfter(date)) {
            n = next(n);
        }
        return dueDate(start, n);
    }

    /** A guess of the occurrence number around {@code date}; the callers correct it. */
    private int estimate(LocalDate start, LocalDate date) {
        long periods = switch (this) {
            case WEEKLY -> ChronoUnit.WEEKS.between(start, date);
            case BIWEEKLY -> ChronoUnit.WEEKS.between(start, date) / 2;
            case MONTHLY -> ChronoUnit.MONTHS.between(start, date);
            case YEARLY -> ChronoUnit.YEARS.between(start, date);
        };
        return (int) Math.min(periods, MAX_OCCURRENCES);
    }

    private static int next(int n) {
        if (n >= MAX_OCCURRENCES) {
            throw new IllegalStateException("Date too far from the start of the recurrence.");
        }
        return n + 1;
    }
}
