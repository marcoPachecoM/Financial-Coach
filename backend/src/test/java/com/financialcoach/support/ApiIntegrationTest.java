package com.financialcoach.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.financialcoach.TestcontainersConfiguration;
import com.financialcoach.auth.TokenService;
import com.financialcoach.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for HTTP-level tests: full application, real PostgreSQL (Testcontainers), MockMvc
 * with Spring Security. Each test runs in a transaction that is rolled back at the end.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class ApiIntegrationTest {

    protected static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokens;

    protected MockMvc mvc;
    protected TestUsers users;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        users = new TestUsers(jdbc, passwordEncoder);
    }

    /** Authenticates the request as the given user with a real access token (no login request). */
    protected RequestPostProcessor as(TestUser user) {
        String token = tokens.createAccessToken(user.id(), user.role());
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
