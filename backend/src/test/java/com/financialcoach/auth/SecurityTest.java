package com.financialcoach.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** Access-token authentication and role checks on regular endpoints. */
class SecurityTest extends ApiIntegrationTest {

    private static final String PROTECTED = "/api/v1/health/protected";

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private TestJwts jwts;
    private TestUser admin;
    private TestUser client;

    @BeforeEach
    void createUsers() {
        jwts = new TestJwts(encoder, properties);
        admin = users.admin();
        client = users.client();
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mvc.perform(get("/api/v1/health/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").exists());
    }

    @Test
    void withoutTokenReturns401Problem() throws Exception {
        mvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value(PROTECTED))
                // Same shape as errors from ApiExceptionHandler: "type" omitted (about:blank).
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void httpBasicIsNoLongerAccepted() throws Exception {
        mvc.perform(get(PROTECTED).header("Authorization", "Basic Y2xpZW50QGV4YW1wbGUudGVzdDpUZXN0UGFzczEyMyE="))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void clientCanAccessProtectedEndpoint() throws Exception {
        mvc.perform(get(PROTECTED).with(as(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void clientCannotAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/health/admin").with(as(client)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/health/admin").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("admin"));
    }

    @Test
    void adminIsAlsoAuthenticatedForRegularEndpoints() throws Exception {
        mvc.perform(get(PROTECTED).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    /** Guards the tests below: with no changes, the helper's token is accepted. */
    @Test
    void helperTokenIsValid() throws Exception {
        withToken(jwts.access(client.id(), "CLIENT", claims -> { })).andExpect(status().isOk());
    }

    @Test
    void garbageToken() throws Exception {
        expectInvalidToken(withToken("not-a-jwt"));
    }

    @Test
    void expiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        // More than the 60 s of clock skew that the validator tolerates.
        expectInvalidToken(withToken(access(c -> c.issuedAt(past).notBefore(past).expiresAt(Instant.now().minusSeconds(120)))));
    }

    @Test
    void tokenNotValidYet() throws Exception {
        expectInvalidToken(withToken(access(c -> c.notBefore(Instant.now().plusSeconds(300)))));
    }

    @Test
    void refreshTokenIsNotAnAccessToken() throws Exception {
        expectInvalidToken(withToken(jwts.refresh(client.id(), c -> { })));
    }

    @Test
    void wrongIssuer() throws Exception {
        expectInvalidToken(withToken(access(c -> c.issuer("someone-else"))));
    }

    @Test
    void wrongAudience() throws Exception {
        expectInvalidToken(withToken(access(c -> c.audience(List.of("another-app")))));
    }

    @Test
    void missingRequiredClaim() throws Exception {
        expectInvalidToken(withToken(access(c -> c.claims(all -> all.remove("jti")))));
    }

    @Test
    void unknownRole() throws Exception {
        expectInvalidToken(withToken(jwts.access(client.id(), "SUPERUSER", c -> { })));
    }

    @Test
    void subjectMustBeAUserId() throws Exception {
        expectInvalidToken(withToken(access(c -> c.subject("client@example.test"))));
    }

    @Test
    void signedWithAnotherKey() throws Exception {
        expectInvalidToken(withToken(jwts.signedWithAnotherKey(client.id())));
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        expectInvalidToken(withToken(jwts.unsigned(admin.id())));
    }

    private String access(Consumer<JwtClaimsSet.Builder> customize) {
        return jwts.access(client.id(), "CLIENT", customize);
    }

    private ResultActions withToken(String token) throws Exception {
        return mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token));
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or expired."));
    }
}
