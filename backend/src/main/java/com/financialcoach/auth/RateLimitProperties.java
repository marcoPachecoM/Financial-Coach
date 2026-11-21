package com.financialcoach.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code financial-coach.rate-limit.*}: requests per minute and client IP. */
@ConfigurationProperties("financial-coach.rate-limit")
record RateLimitProperties(int login, int refresh, int logout) {
}
