package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.categorization.CategorizationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecurringMatcherTest {

    @ParameterizedTest(name = "\"{0}\" vs \"{1}\" -> {2}")
    @CsvSource({
            "Netflix,             Netflix,                 true",
            "Netflix,             NETFLIX.COM 866-579,     true",
            "Spotify Premium,     spotify,                 true",
            "Netflix,             Netflixy,                false",
            "Netflix,             Spotify,                 false",
            "Gym,                 Gymboree,                false",
            "Netflix,             '123',                   false",
    })
    void sameMerchant(String a, String b, boolean expected) {
        assertThat(RecurringMatcher.sameMerchant(
                CategorizationService.merchantKey(a), CategorizationService.merchantKey(b)))
                .isEqualTo(expected);
    }
}
