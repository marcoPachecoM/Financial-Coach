package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.transactions.RecurringDetector.Charge;
import com.financialcoach.transactions.RecurringDetector.Series;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 21);

    @Test
    void findsAMonthlySeries() {
        List<Charge> charges = List.of(
                charge("spotify", "2026-11-12", "129.00"),
                charge("spotify", "2026-08-12", "129.00"),
                charge("spotify", "2026-10-11", "129.00"),
                charge("spotify", "2026-09-13", "129.00"));

        List<Series> series = RecurringDetector.detect(charges, Set.of(), AS_OF);

        assertThat(series).singleElement().satisfies(found -> {
            assertThat(found.frequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
            assertThat(found.occurrences()).isEqualTo(4);
            assertThat(found.latest().date()).isEqualTo("2026-11-12");
        });
    }

    @Test
    void findsAWeeklySeries() {
        List<Charge> charges = List.of(
                charge("gym", "2026-10-31", "50"),
                charge("gym", "2026-11-07", "50"),
                charge("gym", "2026-11-14", "50"),
                charge("gym", "2026-11-21", "50"));

        assertThat(RecurringDetector.detect(charges, Set.of(), AS_OF))
                .singleElement()
                .extracting(Series::frequency)
                .isEqualTo(RecurrenceFrequency.WEEKLY);
    }

    @Test
    void ignoresIrregularCharges() {
        List<Charge> charges = List.of(
                charge("uber", "2026-11-01", "90"),
                charge("uber", "2026-11-03", "120"),
                charge("uber", "2026-11-20", "85"));

        assertThat(RecurringDetector.detect(charges, Set.of(), AS_OF)).isEmpty();
    }

    @Test
    void needsAtLeastThreeCharges() {
        List<Charge> charges = List.of(
                charge("netflix", "2026-10-05", "199"),
                charge("netflix", "2026-11-05", "199"));

        assertThat(RecurringDetector.detect(charges, Set.of(), AS_OF)).isEmpty();
    }

    @Test
    void amountsMayVaryUpToTenPercent() {
        List<Charge> withinTolerance = List.of(
                charge("electricity", "2026-09-10", "500"),
                charge("electricity", "2026-10-10", "540"),
                charge("electricity", "2026-11-10", "460"));
        List<Charge> tooDifferent = List.of(
                charge("market", "2026-09-10", "500"),
                charge("market", "2026-10-10", "800"),
                charge("market", "2026-11-10", "510"));

        assertThat(RecurringDetector.detect(withinTolerance, Set.of(), AS_OF)).hasSize(1);
        assertThat(RecurringDetector.detect(tooDifferent, Set.of(), AS_OF)).isEmpty();
    }

    @Test
    void aSeriesThatStoppedIsNotSuggested() {
        List<Charge> charges = List.of(
                charge("hbo", "2026-07-01", "149"),
                charge("hbo", "2026-08-01", "149"),
                charge("hbo", "2026-09-01", "149"));

        assertThat(RecurringDetector.detect(charges, Set.of(), AS_OF)).isEmpty();
    }

    @Test
    void skipsExcludedMerchants() {
        List<Charge> charges = new ArrayList<>();
        for (String date : List.of("2026-09-12", "2026-10-12", "2026-11-12")) {
            charges.add(charge("spotify", date, "129"));
        }

        assertThat(RecurringDetector.detect(charges, Set.of("spotify"), AS_OF)).isEmpty();
    }

    private static Charge charge(String merchant, String date, String amount) {
        return new Charge(UUID.randomUUID(), merchant, merchant, LocalDate.parse(date), new BigDecimal(amount));
    }
}
