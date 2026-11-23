package com.financialcoach.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderQuotaTest {

    @Test
    void readsGeminisRetryInFromAnyCause() {
        RuntimeException error = new RuntimeException("Failed to generate content",
                new RuntimeException("429 . You exceeded your current quota. Please retry in 6h50m44.913668754s."));

        assertThat(ProviderQuota.of(error)).hasValueSatisfying(quota -> {
            assertThat(quota.retryIn()).isEqualTo(Duration.ofHours(6).plusMinutes(50).plusSeconds(45));
            assertThat(quota.detail()).isEqualTo("The AI model's usage quota is used up. Try again in 6 h 51 min.");
        });
    }

    @Test
    void fallsBackToRetryDelay() {
        RuntimeException error = new RuntimeException("429 RESOURCE_EXHAUSTED {\"retryDelay\": \"24644s\"}");

        assertThat(ProviderQuota.of(error)).map(ProviderQuota::retryIn).contains(Duration.ofSeconds(24644));
    }

    @Test
    void withoutATimeItSaysLater() {
        assertThat(ProviderQuota.of(new RuntimeException("RESOURCE_EXHAUSTED")))
                .map(ProviderQuota::detail).contains("The AI model's usage quota is used up. Try again later.");
    }

    @Test
    void otherErrorsAreNotQuota() {
        assertThat(ProviderQuota.of(new RuntimeException("Failed", new RuntimeException("503 UNAVAILABLE")))).isEmpty();
        assertThat(ProviderQuota.of(new RuntimeException((String) null))).isEmpty();
    }

    @Test
    void roundsUpForPeople() {
        assertThat(ProviderQuota.human(Duration.ofSeconds(1))).isEqualTo("1 second");
        assertThat(ProviderQuota.human(Duration.ofSeconds(42))).isEqualTo("42 seconds");
        assertThat(ProviderQuota.human(Duration.ofSeconds(90))).isEqualTo("2 min");
        assertThat(ProviderQuota.human(Duration.ofHours(2))).isEqualTo("2 h");
    }
}
