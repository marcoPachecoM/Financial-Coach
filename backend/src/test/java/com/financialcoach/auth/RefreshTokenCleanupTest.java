package com.financialcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** "Now" is 2026-11-21 10:00 local time. */
@Import(FixedClockConfiguration.class)
class RefreshTokenCleanupTest extends ApiIntegrationTest {

    @Autowired
    private RefreshTokenCleanup cleanup;

    @Test
    void deletesOnlyTokensExpiredMoreThanADayAgo() {
        TestUser alice = users.create("alice", UserRole.CLIENT, true);
        Instant now = FixedClockConfiguration.TODAY.atTime(10, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant();
        insertToken(alice, "expired-long-ago", now.minus(java.time.Duration.ofDays(3)));
        insertToken(alice, "expired-recently", now.minus(java.time.Duration.ofHours(2)));
        insertToken(alice, "still-valid", now.plus(java.time.Duration.ofDays(10)));

        assertThat(cleanup.deleteExpired()).isEqualTo(1);

        List<String> left = jdbc.queryForList("SELECT jti FROM refresh_tokens ORDER BY jti", String.class);
        assertThat(left).containsExactly("expired-recently", "still-valid");
    }

    private void insertToken(TestUser user, String jti, Instant expiresAt) {
        jdbc.update("INSERT INTO refresh_tokens (jti, user_id, expires_at) VALUES (?, ?, ?)",
                jti, user.id(), Timestamp.from(expiresAt));
    }
}
