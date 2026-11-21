package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecurrenceFrequencyTest {

    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Test
    void monthlyDueDatesAreComputedFromTheStartAndDoNotDrift() {
        assertThat(RecurrenceFrequency.MONTHLY.dueDate(JAN_31, 1)).isEqualTo("2026-02-28");
        assertThat(RecurrenceFrequency.MONTHLY.dueDate(JAN_31, 2)).isEqualTo("2026-03-31");
        assertThat(RecurrenceFrequency.MONTHLY.dueDate(JAN_31, 3)).isEqualTo("2026-04-30");
    }

    @Test
    void yearlyFromLeapDay() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        assertThat(RecurrenceFrequency.YEARLY.dueDate(leapDay, 1)).isEqualTo("2029-02-28");
        assertThat(RecurrenceFrequency.YEARLY.dueDate(leapDay, 4)).isEqualTo("2032-02-29");
    }

    @ParameterizedTest(name = "{0} from 2026-01-31 after {1} -> {2}")
    @CsvSource({
            "MONTHLY,  2026-01-31, 2026-02-28",
            "MONTHLY,  2026-02-27, 2026-02-28",
            "MONTHLY,  2026-02-28, 2026-03-31",
            "MONTHLY,  2025-12-01, 2026-01-31",
            "WEEKLY,   2026-02-07, 2026-02-14",
            "WEEKLY,   2026-02-06, 2026-02-07",
            "BIWEEKLY, 2026-02-14, 2026-02-28",
            "YEARLY,   2026-06-01, 2027-01-31",
    })
    void nextAfter(RecurrenceFrequency frequency, LocalDate after, LocalDate expected) {
        assertThat(frequency.nextAfter(JAN_31, after)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}: nearest to {1} -> {2}")
    @CsvSource({
            "MONTHLY, 2026-03-02, 2026-02-28",
            "MONTHLY, 2026-03-29, 2026-03-31",
            "MONTHLY, 2026-01-20, 2026-01-31",
            "WEEKLY,  2026-02-10, 2026-02-07",
            "WEEKLY,  2026-02-11, 2026-02-14",
    })
    void nearest(RecurrenceFrequency frequency, LocalDate date, LocalDate expected) {
        assertThat(frequency.nearest(JAN_31, date)).isEqualTo(expected);
    }
}
