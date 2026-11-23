package com.financialcoach.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's clock with a {@link MutableClock} set to {@link #TODAY} at 10:00,
 * for tests whose results depend on "today" (recurring transactions, alerts).
 * Each test should call {@code clock.setToday(...)} if it changes the date.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(TODAY, ZoneId.systemDefault());
    }

    /** A clock whose date tests can move; always 10:00 local time. */
    public static final class MutableClock extends Clock {

        private final ZoneId zone;
        private volatile Instant instant;

        MutableClock(LocalDate today, ZoneId zone) {
            this.zone = zone;
            setToday(today);
        }

        public void setToday(LocalDate today) {
            this.instant = today.atTime(10, 0).atZone(zone).toInstant();
        }

        /** Moves the clock forward, e.g. past a rate-limit window. */
        public void advance(java.time.Duration duration) {
            this.instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return Clock.fixed(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
