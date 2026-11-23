package com.financialcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AuthControllerTest extends ApiIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String ME = "/api/v1/auth/me";

    @Autowired
    private RateLimiter rateLimiter;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private TestJwts jwts;
    private TestUser client;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        jwts = new TestJwts(encoder, properties);
        client = users.client();
    }

    @Nested
    class Login {

        @Test
        void returnsATokenPair() throws Exception {
            Tokens tokens = login(client.email(), client.password());

            assertThat(tokens.access()).isNotBlank();
            assertThat(tokens.refresh()).isNotBlank().isNotEqualTo(tokens.access());
            mvc.perform(get(ME).header("Authorization", "Bearer " + tokens.access()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.id").value(client.id().toString()));
            // Only the jti is stored, never the token.
            syncDatabase();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                    Integer.class, client.id())).isEqualTo(1);
        }

        @Test
        void emailIsCaseInsensitiveAndTrimmed() throws Exception {
            postLogin("  " + client.email().toUpperCase() + " ", client.password()).andExpect(status().isOk());
        }

        @Test
        void wrongPassword() throws Exception {
            postLogin(client.email(), "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        }

        @Test
        void unknownEmailGetsTheSameAnswerAsWrongPassword() throws Exception {
            postLogin("nobody@example.test", client.password())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        }

        @Test
        void inactiveUserGetsTheSameAnswer() throws Exception {
            TestUser inactive = users.create("inactive", UserRole.CLIENT, false);

            postLogin(inactive.email(), inactive.password())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        void validatesTheBody() throws Exception {
            mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "not-an-email", "password": ""}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("email"))
                    .andExpect(jsonPath("$.errors[1].field").value("password"));
        }

        @Test
        void unknownFieldsAreRejected() throws Exception {
            mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "%s", "password": "%s", "role": "ADMIN"}
                            """.formatted(client.email(), client.password())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("role"));
        }

        @Test
        void aStaleAccessTokenDoesNotBlockLogin() throws Exception {
            mvc.perform(post(LOGIN).header("Authorization", "Bearer expired-or-garbage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(client.email(), client.password())))
                    .andExpect(status().isOk());
        }

        @Test
        void isRateLimitedToFivePerMinute() throws Exception {
            for (int i = 0; i < 5; i++) {
                postLogin(client.email(), "wrong-password").andExpect(status().isUnauthorized());
            }

            postLogin(client.email(), client.password())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesTheTokenPair() throws Exception {
            Tokens first = login(client.email(), client.password());

            Tokens second = refresh(first.refresh());

            assertThat(second.refresh()).isNotEqualTo(first.refresh());
            assertThat(second.access()).isNotEqualTo(first.access());
            mvc.perform(get(ME).header("Authorization", "Bearer " + second.access())).andExpect(status().isOk());

            Map<String, Object> old = refreshTokenRow(first.refresh());
            assertThat(old.get("revoked_at")).isNotNull();
            assertThat(old.get("replaced_by_jti")).isEqualTo(jti(second.refresh()));
        }

        @Test
        void reusingARotatedTokenClosesEverySession() throws Exception {
            Tokens laptop = login(client.email(), client.password());
            Tokens phone = login(client.email(), client.password());
            Tokens rotated = refresh(laptop.refresh());

            // Someone replays the laptop's old token (e.g. stolen before it was rotated).
            postRefresh(laptop.refresh())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE"));

            assertThat(refreshTokenRow(laptop.refresh()).get("reuse_detected_at")).isNotNull();
            // Both the attacker's and the user's newer tokens are dead: everyone must log in again.
            postRefresh(rotated.refresh()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
            postRefresh(phone.refresh()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        void otherUsersAreNotAffectedByReuse() throws Exception {
            TestUser other = users.create("other", UserRole.CLIENT, true);
            Tokens othersTokens = login(other.email(), other.password());
            Tokens mine = login(client.email(), client.password());
            refresh(mine.refresh());

            postRefresh(mine.refresh()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE"));

            postRefresh(othersTokens.refresh()).andExpect(status().isOk());
        }

        @Test
        void accessTokenIsNotARefreshToken() throws Exception {
            Tokens tokens = login(client.email(), client.password());

            postRefresh(tokens.access())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void missingToken() throws Exception {
            mvc.perform(post(REFRESH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        void notABearerHeader() throws Exception {
            mvc.perform(post(REFRESH).header("Authorization", "Basic abc"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void garbageToken() throws Exception {
            postRefresh("garbage").andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void expiredToken() throws Exception {
            Instant past = Instant.now().minusSeconds(3600);
            String expired = jwts.refresh(client.id(),
                    c -> c.issuedAt(past).notBefore(past).expiresAt(Instant.now().minusSeconds(120)));

            postRefresh(expired).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void validSignatureButUnknownToken() throws Exception {
            postRefresh(jwts.refresh(client.id(), c -> { }))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        void deactivatedUserCannotRefresh() throws Exception {
            Tokens tokens = login(client.email(), client.password());
            syncDatabase();
            jdbc.update("UPDATE users SET is_active = false WHERE id = ?", client.id());

            postRefresh(tokens.refresh()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        void newAccessTokenCarriesTheCurrentRole() throws Exception {
            Tokens tokens = login(client.email(), client.password());
            syncDatabase();
            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", client.id());

            Tokens refreshed = refresh(tokens.refresh());

            mvc.perform(get("/api/v1/health/admin").header("Authorization", "Bearer " + refreshed.access()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Logout {

        @Test
        void revokesTheRefreshToken() throws Exception {
            Tokens tokens = login(client.email(), client.password());

            postLogout(tokens.refresh())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out."));

            // Revoked by logout, not rotated: invalid, but not treated as theft.
            postRefresh(tokens.refresh()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        void isIdempotent() throws Exception {
            Tokens tokens = login(client.email(), client.password());

            postLogout(tokens.refresh()).andExpect(status().isOk());
            postLogout(tokens.refresh()).andExpect(status().isOk());
        }

        @Test
        void onlyEndsThatSession() throws Exception {
            Tokens laptop = login(client.email(), client.password());
            Tokens phone = login(client.email(), client.password());

            postLogout(laptop.refresh()).andExpect(status().isOk());

            postRefresh(phone.refresh()).andExpect(status().isOk());
        }

        @Test
        void requiresARefreshToken() throws Exception {
            Tokens tokens = login(client.email(), client.password());

            postLogout(tokens.access()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            mvc.perform(post(LOGOUT)).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        /** Access tokens are stateless: they stay valid until they expire (15 min by default). */
        @Test
        void accessTokenKeepsWorkingUntilItExpires() throws Exception {
            Tokens tokens = login(client.email(), client.password());
            postLogout(tokens.refresh()).andExpect(status().isOk());

            mvc.perform(get(ME).header("Authorization", "Bearer " + tokens.access())).andExpect(status().isOk());
        }
    }

    @Nested
    class Me {

        @Test
        void returnsTheCurrentUser() throws Exception {
            mvc.perform(get(ME).with(as(client)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.id").value(client.id().toString()))
                    .andExpect(jsonPath("$.user.username").value("client"))
                    .andExpect(jsonPath("$.user.email").value(client.email()))
                    .andExpect(jsonPath("$.user.role").value("CLIENT"))
                    .andExpect(jsonPath("$.user.is_active").value(true))
                    .andExpect(jsonPath("$.user.password_hash").doesNotExist());
        }

        @Test
        void requiresAnAccessToken() throws Exception {
            mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        }

        @Test
        void deletedUser() throws Exception {
            String token = jwts.access(UUID.randomUUID(), "CLIENT", c -> { });

            mvc.perform(get(ME).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }

    private record Tokens(String access, String refresh) {
    }

    private Tokens login(String email, String password) throws Exception {
        return tokens(postLogin(email, password).andExpect(status().isOk()).andReturn());
    }

    private Tokens refresh(String refreshToken) throws Exception {
        return tokens(postRefresh(refreshToken).andExpect(status().isOk()).andReturn());
    }

    private ResultActions postLogin(String email, String password) throws Exception {
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email, password)));
    }

    private ResultActions postRefresh(String refreshToken) throws Exception {
        return mvc.perform(post(REFRESH).header("Authorization", "Bearer " + refreshToken));
    }

    private ResultActions postLogout(String refreshToken) throws Exception {
        return mvc.perform(post(LOGOUT).header("Authorization", "Bearer " + refreshToken));
    }

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private static Tokens tokens(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
    }

    /**
     * All requests of a test share one transaction and one Hibernate persistence context (in
     * production each request has its own). Flushing and clearing it simulates the boundary
     * between requests: JDBC sees what the last request wrote, and the next request reloads
     * what JDBC changed.
     */
    private void syncDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private Map<String, Object> refreshTokenRow(String refreshToken) {
        syncDatabase();
        return jdbc.queryForMap("SELECT * FROM refresh_tokens WHERE jti = ?", jti(refreshToken));
    }

    /** Reads the jti from the token payload (no signature check needed here). */
    private static String jti(String token) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return JsonPath.read(payload, "$.jti");
    }
}
