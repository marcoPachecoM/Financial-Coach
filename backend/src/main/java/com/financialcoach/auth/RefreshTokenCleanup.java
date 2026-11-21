package com.financialcoach.auth;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes expired refresh tokens once a day (otherwise refresh_tokens grows forever).
 *
 * <p>Safe for reuse detection: an expired token is rejected by its {@code exp} claim before
 * the database is checked, so its row is no longer needed. The one-day margin covers the
 * clock skew allowed by the validator.
 */
@Component
@ConditionalOnProperty(prefix = "financial-coach.refresh-token-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);
    static final Duration MARGIN = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    RefreshTokenCleanup(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial-coach.refresh-token-cleanup.cron:0 30 3 * * *}")
    @Transactional
    int deleteExpired() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(MARGIN));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
        return deleted;
    }
}
