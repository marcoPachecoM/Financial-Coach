package com.financialcoach.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Slows down password guessing against one account. The per-IP limit alone does not stop an
 * attacker who spreads the attempts over many addresses (password spraying, botnets).
 *
 * <p>After {@code freeFailures} consecutive failures for an email, the next attempt must wait
 * 30 s, then 1 min, 2 min... up to 15 min. A successful login, or an hour without failures,
 * starts again from zero.
 *
 * <ul>
 *   <li>Keyed by the normalized email whether the account exists or not, so the answer never
 *       reveals which emails are registered.
 *   <li>It rejects (429 with Retry-After) instead of sleeping, so it does not hold request threads,
 *       and a rejected attempt skips the expensive password hash.
 *   <li>It is a short, growing delay rather than a lockout: an attacker can only keep a victim
 *       waiting while they keep failing (and the per-IP limit caps how fast), never lock the
 *       account for good.
 * </ul>
 *
 * <p>Like the per-IP limiter, the counters live in memory: they reset on restart and each
 * instance counts on its own.
 */
@Component
class LoginThrottle {

    static final Duration BASE_DELAY = Duration.ofSeconds(30);
    static final Duration MAX_DELAY = Duration.ofMinutes(15);
    static final Duration FORGET_AFTER = Duration.ofHours(1);
    /** Above this many tracked emails, the forgotten ones are removed (attackers may try many). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final int freeFailures;
    private final Clock clock;

    LoginThrottle(RateLimitProperties limits, Clock clock) {
        this.freeFailures = limits.loginAccountFailures();
        this.clock = clock;
    }

    /** Seconds this email must wait before trying again; 0 when it may try now. */
    long secondsUntilAllowed(String email) {
        Failures entry = failures.get(key(email));
        if (entry == null || entry.blockedUntil() == null) {
            return 0;
        }
        long millis = Duration.between(clock.instant(), entry.blockedUntil()).toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    void recordFailure(String email) {
        Instant now = clock.instant();
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.values().removeIf(entry -> entry.isForgotten(now));
        }
        failures.compute(key(email), (k, entry) -> {
            int count = entry == null || entry.isForgotten(now) ? 1 : entry.count() + 1;
            Instant blockedUntil = count < freeFailures ? null : now.plus(delayAfter(count));
            return new Failures(count, now, blockedUntil);
        });
    }

    void recordSuccess(String email) {
        failures.remove(key(email));
    }

    /** For tests. */
    void reset() {
        failures.clear();
    }

    /** 30 s at the threshold, doubling with each further failure, up to {@link #MAX_DELAY}. */
    private Duration delayAfter(int count) {
        int doublings = Math.min(count - freeFailures, 10);
        Duration delay = BASE_DELAY.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    /** Same normalization as the login: surrounding spaces and case do not matter. */
    private static String key(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private record Failures(int count, Instant lastFailure, Instant blockedUntil) {

        boolean isForgotten(Instant now) {
            return !lastFailure.plus(FORGET_AFTER).isAfter(now);
        }
    }
}
