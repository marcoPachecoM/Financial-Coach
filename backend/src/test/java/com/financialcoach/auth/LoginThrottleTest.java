package com.financialcoach.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Failed logins per email, from any IP. The per-IP limit is raised so it does not get in the way;
 * 3 failures before each attempt must wait.
 */
@Import(FixedClockConfiguration.class)
@TestPropertySource(properties = {
        "financial-coach.rate-limit.login=1000",
        "financial-coach.rate-limit.login-account-failures=3"})
class LoginThrottleTest extends ApiIntegrationTest {

    private static final String WRONG = "wrong-password";

    @Autowired
    private LoginThrottle throttle;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private MutableClock clock;

    private TestUser client;

    @BeforeEach
    void setUp() {
        throttle.reset();
        rateLimiter.reset();
        clock.setToday(FixedClockConfiguration.TODAY);
        client = users.client();
    }

    @Test
    void afterTheFailuresEvenTheRightPasswordMustWait() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(client.email(), WRONG).andExpect(status().isUnauthorized());
        }

        login(client.email(), client.password())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Too many failed sign-in attempts. Try again in 30 seconds."));

        clock.advance(Duration.ofSeconds(30));
        login(client.email(), client.password()).andExpect(status().isOk());
        // The success started again from zero: one more failure is a plain 401.
        login(client.email(), WRONG).andExpect(status().isUnauthorized());
        login(client.email(), client.password()).andExpect(status().isOk());
    }

    @Test
    void theWaitDoublesWithEachFailureUpToFifteenMinutes() throws Exception {
        login(client.email(), WRONG);
        login(client.email(), WRONG);
        for (long expected : List.of(30L, 60L, 120L, 240L, 480L, 900L, 900L)) {
            login(client.email(), WRONG).andExpect(status().isUnauthorized());
            login(client.email(), WRONG).andExpect(header().string("Retry-After", Long.toString(expected)));
            clock.advance(Duration.ofSeconds(expected));
        }
    }

    @Test
    void unknownEmailsAreThrottledTheSameWay() throws Exception {
        // Otherwise the 429 would reveal which emails have an account.
        for (int i = 0; i < 3; i++) {
            login("nobody@example.test", WRONG).andExpect(status().isUnauthorized());
        }

        login("nobody@example.test", WRONG)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many failed sign-in attempts. Try again in 30 seconds."));
    }

    @Test
    void caseAndSpacesDoNotMakeANewCounter() throws Exception {
        login(client.email(), WRONG);
        login(client.email().toUpperCase(), WRONG);
        login("  " + client.email() + " ", WRONG);

        login(client.email(), client.password()).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherAccountsAreNotAffected() throws Exception {
        TestUser other = users.create("other", client.role(), true);
        for (int i = 0; i < 3; i++) {
            login(client.email(), WRONG);
        }

        login(other.email(), other.password()).andExpect(status().isOk());
    }

    @Test
    void anHourWithoutFailuresStartsAgain() throws Exception {
        login(client.email(), WRONG);
        login(client.email(), WRONG);
        clock.advance(Duration.ofHours(1));
        login(client.email(), WRONG);
        login(client.email(), WRONG);

        // 2 + 2 failures, but the first two were forgotten: not 3 in a row.
        login(client.email(), client.password()).andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
