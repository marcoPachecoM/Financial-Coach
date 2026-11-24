package com.financialcoach.goals;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.goals.GoalProjection.Entry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Progress, pace and projection rules. "Today" is 2026-11-21. */
class GoalProjectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate LONG_AGO = LocalDate.of(2026, 1, 1);

    /** 900 a month for the last 3 months (and an older one outside the window): pace 900. */
    private static final List<Entry> STEADY = List.of(
            entry("2026-08-01", "900"), // before the 90-day window (starts 2026-08-24)
            entry("2026-09-15", "900"),
            entry("2026-10-15", "900"),
            entry("2026-11-15", "900"));

    @Test
    void onTrackWhenThePaceReachesTheTargetByItsDate() {
        GoalProjection p = project("10000", "2027-06-30", LONG_AGO, STEADY);

        assertThat(p.saved()).isEqualByComparingTo("3600");
        assertThat(p.remaining()).isEqualByComparingTo("6400");
        assertThat(p.percentage()).isEqualByComparingTo("36.00");
        assertThat(p.monthlyPace()).isEqualByComparingTo("900.00");
        assertThat(p.requiredMonthly()).isEqualByComparingTo("914.29"); // 6400 over 7 months, rounded up
        assertThat(p.projectedDate()).isEqualTo(LocalDate.of(2027, 6, 23)); // 6400 / 900 × 30 = 213.3 → 214 days
        assertThat(p.status()).isEqualTo(GoalStatus.ON_TRACK);
    }

    @Test
    void behindWhenThePaceArrivesAfterTheDate() {
        GoalProjection p = project("10000", "2027-05-31", LONG_AGO, STEADY);

        assertThat(p.requiredMonthly()).isEqualByComparingTo("1066.67");
        assertThat(p.status()).isEqualTo(GoalStatus.BEHIND);
    }

    @Test
    void withoutContributionsThereIsNoProjectionAndADatedGoalIsBehind() {
        GoalProjection p = project("1000", "2027-01-31", LONG_AGO, List.of());

        assertThat(p.monthlyPace()).isEqualByComparingTo("0");
        assertThat(p.projectedDate()).isNull();
        assertThat(p.requiredMonthly()).isEqualByComparingTo("500.00");
        assertThat(p.status()).isEqualTo(GoalStatus.BEHIND);
    }

    @Test
    void aYoungGoalIsNotPenalizedByTheThreeMonthWindow() {
        // Created 20 days ago: the window is 30 days (its minimum), not 90.
        GoalProjection p = project("10000", null, LocalDate.of(2026, 11, 1), List.of(entry("2026-11-05", "600")));

        assertThat(p.monthlyPace()).isEqualByComparingTo("600.00");
        assertThat(p.status()).isEqualTo(GoalStatus.NO_TARGET_DATE);
        assertThat(p.requiredMonthly()).isNull();
        assertThat(p.projectedDate()).isEqualTo(TODAY.plusDays(470)); // 9400 / 600 × 30
    }

    @Test
    void aBackdatedContributionStartsTheWindow() {
        // Created yesterday, with money put in 61 days ago: the window is those 61 days.
        GoalProjection p = project("10000", null, LocalDate.of(2026, 11, 20), List.of(entry("2026-09-22", "1220")));

        assertThat(p.monthlyPace()).isEqualByComparingTo("600.00");
    }

    @Test
    void withdrawalsReduceTheSavedAmountAndThePace() {
        GoalProjection p = project("10000", null, LocalDate.of(2026, 11, 1),
                List.of(entry("2026-11-01", "1000"), entry("2026-11-10", "-400")));

        assertThat(p.saved()).isEqualByComparingTo("600");
        assertThat(p.monthlyPace()).isEqualByComparingTo("600.00");
    }

    @Test
    void overdueOnceTheDatePassesWithoutReachingTheTarget() {
        GoalProjection p = project("1000", "2026-11-01", LONG_AGO, List.of(entry("2026-10-01", "100")));

        assertThat(p.status()).isEqualTo(GoalStatus.OVERDUE);
        assertThat(p.requiredMonthly()).isNull();
    }

    @Test
    void aDateLaterThisMonthNeedsEverythingThatIsLeft() {
        GoalProjection p = project("1000", "2026-11-30", LONG_AGO, List.of(entry("2026-11-02", "250")));

        assertThat(p.requiredMonthly()).isEqualByComparingTo("750.00");
    }

    @Test
    void completedOnceTheTargetIsReachedEvenPastItsDate() {
        GoalProjection exact = project("1000", "2026-11-01", LONG_AGO, List.of(entry("2026-10-01", "1000")));
        GoalProjection over = project("1000", null, LONG_AGO, List.of(entry("2026-10-01", "1200")));

        assertThat(exact.status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(exact.remaining()).isEqualByComparingTo("0");
        assertThat(exact.projectedDate()).isNull();
        assertThat(exact.requiredMonthly()).isNull();
        assertThat(over.percentage()).isEqualByComparingTo("120.00");
        assertThat(over.remaining()).isEqualByComparingTo("0");
    }

    private static GoalProjection project(String target, String targetDate, LocalDate createdOn, List<Entry> entries) {
        return GoalProjection.of(new BigDecimal(target), targetDate == null ? null : LocalDate.parse(targetDate),
                createdOn, entries, TODAY);
    }

    private static Entry entry(String date, String amount) {
        return new Entry(LocalDate.parse(date), new BigDecimal(amount));
    }
}
