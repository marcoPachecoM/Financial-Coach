package com.financialcoach.auth;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

class CorsTest extends ApiIntegrationTest {

    private static final String FRONTEND = "http://localhost:5173";

    /** The browser sends this before a request with an Authorization header; it carries no token. */
    @Test
    void preflightFromTheFrontendIsAllowed() throws Exception {
        mvc.perform(options("/api/v1/accounts")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
    }

    @Test
    void errorResponsesAlsoCarryCorsHeaders() throws Exception {
        // Otherwise the frontend could not read the 401 and refresh its token.
        mvc.perform(get("/api/v1/accounts").header("Origin", FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mvc.perform(options("/api/v1/accounts")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
