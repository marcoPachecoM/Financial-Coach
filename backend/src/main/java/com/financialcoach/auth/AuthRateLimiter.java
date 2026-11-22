package com.financialcoach.auth;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed one-minute windows per (endpoint, client IP), kept in memory.
 *
 * <p>Enough for a single instance. With several instances each one counts separately; then the
 * counters should move to a shared store (Redis, as the Flask version allowed).
 */
@Component
class AuthRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    /** Old windows are dropped when the map grows past this size. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts the request. Returns 0 if it is allowed, otherwise the seconds until the client
     * may try again (for the Retry-After header).
     */
    long acquire(String endpoint, String client, int limitPerMinute) {
        long now = clock.millis();
        long windowStart = now - (now % WINDOW_MILLIS);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.start() < windowStart);
        }
        Window window = windows.compute(endpoint + '|' + client, (key, current) ->
                current == null || current.start() != windowStart
                        ? new Window(windowStart, 1)
                        : new Window(windowStart, current.count() + 1));
        if (window.count() <= limitPerMinute) {
            return 0;
        }
        return Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
    }

    /** For tests: forget every counter. */
    void reset() {
        windows.clear();
    }

    private record Window(long start, int count) {
    }
}
