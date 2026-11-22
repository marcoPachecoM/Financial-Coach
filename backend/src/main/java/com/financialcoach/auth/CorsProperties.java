package com.financialcoach.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code financial-coach.cors.*}: browser origins allowed to call the API. */
@ConfigurationProperties("financial-coach.cors")
record CorsProperties(List<String> allowedOrigins) {

    CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "financial-coach.cors.allowed-origins must list explicit origins (no '*')");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
