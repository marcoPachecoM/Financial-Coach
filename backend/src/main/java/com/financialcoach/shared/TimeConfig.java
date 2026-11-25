package com.financialcoach.shared;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock used for "now" (e.g. the default month of the budget endpoint). Injecting it instead
 * of calling {@code LocalDate.now()} keeps time-dependent code testable.
 */
@Configuration
class TimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);

    /**
     * Server time zone (in production TZ and -Duser.timezone, see backend/Dockerfile). Logged at
     * startup because a container defaults to UTC, which would move "today" in the evening. Once
     * users have a time zone setting, "current month" should use it.
     */
    @Bean
    Clock clock() {
        Clock clock = Clock.systemDefaultZone();
        log.info("Clock zone: {}", clock.getZone());
        return clock;
    }
}
