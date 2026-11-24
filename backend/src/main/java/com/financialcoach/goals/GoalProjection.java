package com.financialcoach.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Progress and projection of a goal, computed from its contributions. Pure (no database, no
 * clock), so every rule is unit-tested. Amounts have 2 decimals; a "month" in the pace and the
 * projected date is 30 days.
 *
 * @param monthlyPace     money put in per month lately (see {@link #of}); may be zero or negative
 * @param requiredMonthly per month still needed to reach the target by its date; null without a
 *                        date or once completed
 * @param projectedDate   when the target is reached at the current pace; null when the pace is
 *                        not positive or the goal is completed
 */
record GoalProjection(BigDecimal saved, BigDecimal remaining, BigDecimal percentage, BigDecimal monthlyPace,
                      BigDecimal requiredMonthly, LocalDate projectedDate, GoalStatus status) {

    /** One contribution as the projection needs it. */
    record Entry(LocalDate date, BigDecimal amount) {
    }

    private static final int MIN_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 90;
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * The pace is what was put in during the last 90 days, per 30 days. A young goal is not
     * penalized: the window starts at its creation (or its first contribution, when backdated)
     * and is never shorter than 30 days.
     */
    static GoalProjection of(BigDecimal target, LocalDate targetDate, LocalDate createdOn,
                             Collection<Entry> contributions, LocalDate today) {
        BigDecimal saved = contributions.stream().map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
        BigDecimal percentage = saved.multiply(HUNDRED).divide(target, 2, RoundingMode.HALF_UP);

        LocalDate start = contributions.stream().map(Entry::date).filter(createdOn::isAfter)
                .min(LocalDate::compareTo).orElse(createdOn);
        long windowDays = Math.clamp(ChronoUnit.DAYS.between(start, today) + 1, MIN_WINDOW_DAYS, MAX_WINDOW_DAYS);
        LocalDate windowStart = today.minusDays(windowDays - 1);
        BigDecimal recent = contributions.stream()
                .filter(c -> !c.date().isBefore(windowStart) && !c.date().isAfter(today))
                .map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pace = recent.multiply(DAYS_PER_MONTH).divide(BigDecimal.valueOf(windowDays), 2, RoundingMode.HALF_UP);

        if (remaining.signum() == 0) {
            return new GoalProjection(saved, remaining, percentage, pace, null, null, GoalStatus.COMPLETED);
        }

        LocalDate projected = null;
        if (pace.signum() > 0) {
            long days = remaining.multiply(DAYS_PER_MONTH).divide(pace, 0, RoundingMode.CEILING).longValueExact();
            projected = today.plusDays(days);
        }

        if (targetDate == null) {
            return new GoalProjection(saved, remaining, percentage, pace, null, projected, GoalStatus.NO_TARGET_DATE);
        }
        if (targetDate.isBefore(today)) {
            return new GoalProjection(saved, remaining, percentage, pace, null, projected, GoalStatus.OVERDUE);
        }
        // Calendar months left, at least 1: a target later this month needs everything now.
        long monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)));
        BigDecimal required = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
        GoalStatus status = projected != null && !projected.isAfter(targetDate) ? GoalStatus.ON_TRACK : GoalStatus.BEHIND;
        return new GoalProjection(saved, remaining, percentage, pace, required, projected, status);
    }
}
