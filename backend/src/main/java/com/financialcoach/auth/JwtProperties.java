package com.financialcoach.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code financial-coach.jwt.*} in application.yml. Invalid values stop the application at startup. */
@ConfigurationProperties("financial-coach.jwt")
record JwtProperties(String secret, String issuer, String audience, Duration accessTokenTtl,
                     Duration refreshTokenTtl) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "financial-coach.jwt.secret (JWT_SECRET_KEY) must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("financial-coach.jwt.issuer and audience are required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl == null || refreshTokenTtl.compareTo(accessTokenTtl) <= 0) {
            throw new IllegalArgumentException("JWT lifetimes must be positive and refresh > access");
        }
    }
}
