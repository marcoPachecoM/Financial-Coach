package com.financialcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers;
import com.financialcoach.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Sign-up, recovery codes, password reset and change, admin reset. */
class AccountSecurityTest extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "a much longer passphrase";

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private LoginThrottle loginThrottle;

    @BeforeEach
    void resetLimits() {
        rateLimiter.reset();
        loginThrottle.reset();
    }

    @Nested
    class Register {

        @Test
        void createsAnActiveClientSignedInWithEightRecoveryCodes() throws Exception {
            String body = register("ana", " Ana@Example.com ", "correct horse battery")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recovery_codes", hasSize(8)))
                    .andReturn().getResponse().getContentAsString();

            String access = JsonPath.read(body, "$.access_token");
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value("ana"))
                    .andExpect(jsonPath("$.user.email").value("Ana@Example.com"))
                    .andExpect(jsonPath("$.user.role").value("CLIENT"));
            login("ana@example.com", "correct horse battery").andExpect(status().isOk());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM recovery_codes r JOIN users u ON u.id = r.user_id WHERE u.username = 'ana'
                    """, Integer.class)).isEqualTo(8);
        }

        @Test
        void emailAndUsernameAreUniqueIgnoringCase() throws Exception {
            register("ana", "ana@example.com", "correct horse battery");

            register("other", "ANA@example.com", "correct horse battery")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
            register("ANA", "new@example.com", "correct horse battery")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("This username is already taken."));
        }

        @Test
        void rejectsWeakPasswordsAndInvalidUsernames() throws Exception {
            register("ana", "ana@example.com", "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"))
                    .andExpect(jsonPath("$.errors[0].message").value("must have at least 10 characters"));
            register("ana", "ana.lopez@example.com", "ana.lopez@example.com")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("must not be your email"));
            register("ana lopez", "ana@example.com", "correct horse battery")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("username"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username ILIKE 'ana%'", Integer.class))
                    .isZero();
        }

        @Test
        void isLimitedToThreeSignUpsPerHourAndIp() throws Exception {
            for (int i = 1; i <= 3; i++) {
                register("user" + i, "user" + i + "@example.com", "correct horse battery").andExpect(status().isCreated());
            }
            register("user4", "user4@example.com", "correct horse battery")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    class PasswordReset {

        @Test
        void aRecoveryCodeSetsANewPasswordAndEndsEverySession() throws Exception {
            String body = register("ana", "ana@example.com", "correct horse battery")
                    .andReturn().getResponse().getContentAsString();
            List<String> codes = JsonPath.read(body, "$.recovery_codes");
            String refresh = JsonPath.read(body, "$.refresh_token");

            reset("ana@example.com", codes.get(0).toUpperCase().replace("-", " "), NEW_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recovery_codes_remaining").value(7));

            login("ana@example.com", NEW_PASSWORD).andExpect(status().isOk());
            login("ana@example.com", "correct horse battery").andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
                    .andExpect(status().isUnauthorized());
            // A code works once.
            reset("ana@example.com", codes.get(0), "yet another passphrase")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RECOVERY"));
        }

        @Test
        void unknownEmailsAndOtherUsersCodesGetTheSameAnswer() throws Exception {
            List<String> anaCodes = JsonPath.read(register("ana", "ana@example.com", "correct horse battery")
                    .andReturn().getResponse().getContentAsString(), "$.recovery_codes");
            register("bob", "bob@example.com", "correct horse battery");

            reset("bob@example.com", anaCodes.get(0), NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Invalid email or recovery code."));
            reset("nobody@example.com", anaCodes.get(0), NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Invalid email or recovery code."));
        }

        @Test
        void aWeakNewPasswordDoesNotUseUpTheCode() throws Exception {
            List<String> codes = JsonPath.read(register("ana", "ana@example.com", "correct horse battery")
                    .andReturn().getResponse().getContentAsString(), "$.recovery_codes");

            reset("ana@example.com", codes.get(0), "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("new_password"));
            reset("ana@example.com", codes.get(0), NEW_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void repeatedFailuresForOneEmailAreThrottled() throws Exception {
            register("ana", "ana@example.com", "correct horse battery");
            for (int i = 0; i < 5; i++) {
                // Spread over "different IPs": only the per-email throttle applies.
                rateLimiter.reset();
                reset("ana@example.com", "aaaa-bbbb-cccc", NEW_PASSWORD).andExpect(status().isBadRequest());
            }
            rateLimiter.reset();
            reset("ana@example.com", "aaaa-bbbb-cccc", NEW_PASSWORD).andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    class SignedIn {

        private TestUser client;

        @BeforeEach
        void createClient() {
            client = users.client();
        }

        @Test
        void changingThePasswordEndsOtherSessionsAndKeepsThisOne() throws Exception {
            String oldRefresh = JsonPath.read(login(client.email(), client.password())
                    .andReturn().getResponse().getContentAsString(), "$.refresh_token");

            String body = authed(post("/api/v1/auth/change-password"), client,
                    "{\"current_password\": \"%s\", \"new_password\": \"%s\"}".formatted(client.password(), NEW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String access = JsonPath.read(body, "$.access_token");
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldRefresh))
                    .andExpect(status().isUnauthorized());
            login(client.email(), NEW_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void changingThePasswordNeedsTheCurrentOneAndANewDifferentOne() throws Exception {
            authed(post("/api/v1/auth/change-password"), client,
                    "{\"current_password\": \"wrong\", \"new_password\": \"%s\"}".formatted(NEW_PASSWORD))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("current_password"));
            authed(post("/api/v1/auth/change-password"), client,
                    "{\"current_password\": \"%1$s\", \"new_password\": \"%1$s\"}".formatted(client.password()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("must be different from the current password"));
        }

        @Test
        void newRecoveryCodesReplaceTheOldOnes() throws Exception {
            mvc.perform(get("/api/v1/auth/recovery-codes").with(as(client)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remaining").value(0));

            authed(post("/api/v1/auth/recovery-codes"), client, "{\"current_password\": \"wrong\"}")
                    .andExpect(status().isBadRequest());
            List<String> first = JsonPath.read(authed(post("/api/v1/auth/recovery-codes"), client,
                    "{\"current_password\": \"%s\"}".formatted(client.password()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.recovery_codes");
            authed(post("/api/v1/auth/recovery-codes"), client, "{\"current_password\": \"%s\"}".formatted(client.password()));

            mvc.perform(get("/api/v1/auth/recovery-codes").with(as(client))).andExpect(jsonPath("$.remaining").value(8));
            reset(client.email(), first.get(0), NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        @Test
        void securityEndpointsNeedASignedInUser() throws Exception {
            mvc.perform(get("/api/v1/auth/recovery-codes")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class AdminReset {

        @Test
        void anAdminGetsATemporaryPasswordThatEndsTheUsersSessions() throws Exception {
            TestUser admin = users.admin();
            TestUser client = users.client();
            String oldRefresh = JsonPath.read(login(client.email(), client.password())
                    .andReturn().getResponse().getContentAsString(), "$.refresh_token");

            String temporary = JsonPath.read(adminReset(admin, client.email().toUpperCase())
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.temporary_password");

            assertThat(temporary).hasSize(16);
            login(client.email(), temporary).andExpect(status().isOk());
            login(client.email(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldRefresh))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void onlyAdminsAndOnlyExistingUsers() throws Exception {
            TestUser client = users.client();
            TestUser admin = users.admin();

            adminReset(client, admin.email()).andExpect(status().isForbidden());
            adminReset(admin, "nobody@example.com").andExpect(status().isNotFound());
            adminReset(admin, "not-an-email")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }
    }

    private ResultActions adminReset(TestUser admin, String email) throws Exception {
        return mvc.perform(post("/api/v1/admin/users/password-reset").with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions register(String username, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}".formatted(username, email, password)));
    }

    private ResultActions reset(String email, String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"recovery_code\": \"%s\", \"new_password\": \"%s\"}"
                        .formatted(email, code, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        rateLimiter.reset(); // these tests are not about the login limit
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions authed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 TestUser user, String json) throws Exception {
        return mvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
