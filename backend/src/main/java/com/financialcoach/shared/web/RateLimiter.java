package com.financialcoach.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed windows per key, kept in memory. Used by the token endpoints (per client IP) and the
 * copilot (per user). Windows start on the clock's local time, so a one-day window resets at
 * local midnight and a one-minute window at the start of each minute.
 *
 * <p>Enough for a single instance. With several instances each one counts separately; then the
 * counters should move to a shared store (Redis, as the Flask version allowed).
 */
@Component
public class RateLimiter {

    /** Old windows are dropped when the map grows past this size. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts a request for {@code key}. Returns 0 if it is allowed, otherwise the seconds until
     * the window ends (for the Retry-After header). Each window length has its own counter, so
     * the same key can have a per-minute and a per-day limit.
     */
    public long acquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long length = window.toMillis();
        long windowStart = windowStart(now, length);
        long windowEnd = windowStart + length;

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.end() <= now);
        }
        Window current = windows.compute(key + '|' + length, (k, w) ->
                w == null || w.start() != windowStart
                        ? new Window(windowStart, windowEnd, 1)
                        : new Window(windowStart, windowEnd, w.count() + 1));
        if (current.count() <= limit) {
            return 0;
        }
        return Math.max(1, (windowEnd - now + 999) / 1000);
    }

    /** For tests: forget every counter. */
    public void reset() {
        windows.clear();
    }

    /** Start of the window containing {@code now}, aligned on local time (the zone's UTC offset). */
    private long windowStart(long now, long length) {
        long offset = clock.getZone().getRules().getOffset(Instant.ofEpochMilli(now)).getTotalSeconds() * 1000L;
        long local = now + offset;
        return local - Math.floorMod(local, length) - offset;
    }

    private record Window(long start, long end, int count) {
    }
}
