package com.financialcoach.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.TestcontainersConfiguration;
import com.financialcoach.support.TestUsers;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** HTTP Basic against the users table, roles, and the Problem Details error format. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SecurityTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private TestUsers users;
    private TestUser admin;
    private TestUser client;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        users = new TestUsers(jdbc, passwordEncoder);
        admin = users.admin();
        client = users.client();
    }

    @Test
    void publicEndpointsNeedNoCredentials() throws Exception {
        mvc.perform(get("/api/v1/health/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").exists());
    }

    @Test
    void protectedEndpointWithoutCredentialsReturns401Problem() throws Exception {
        mvc.perform(get("/api/v1/health/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/health/protected"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mvc.perform(get("/api/v1/health/protected").with(httpBasic(client.email(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownUserGetsTheSame401AsWrongPassword() throws Exception {
        mvc.perform(get("/api/v1/health/protected").with(httpBasic("nobody@example.test", TestUsers.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void clientCanAccessProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/v1/health/protected").with(httpBasic(client.email(), client.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void loginEmailIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/v1/health/protected").with(httpBasic(client.email().toUpperCase(), client.password())))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        TestUser inactive = users.create("inactive", UserRole.CLIENT, false);

        mvc.perform(get("/api/v1/health/protected").with(httpBasic(inactive.email(), inactive.password())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/health/admin").with(httpBasic(client.email(), client.password())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/health/admin").with(httpBasic(admin.email(), admin.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("admin"));
    }

    @Test
    void adminIsAlsoAuthenticatedForRegularEndpoints() throws Exception {
        mvc.perform(get("/api/v1/health/protected").with(httpBasic(admin.email(), admin.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
