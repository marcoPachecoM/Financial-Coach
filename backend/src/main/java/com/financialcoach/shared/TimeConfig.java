package com.financialcoach.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock used for "now" (e.g. the default month of the budget endpoint). Injecting it instead
 * of calling {@code LocalDate.now()} keeps time-dependent code testable.
 */
@Configuration
class TimeConfig {

    /** Server time zone. Once users have a time zone setting, "current month" should use it. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
