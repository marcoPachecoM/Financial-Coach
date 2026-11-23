package com.financialcoach.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code financial-coach.rate-limit.*}: requests per minute and client IP on the token endpoints,
 * and the consecutive failed logins of one email before each attempt must wait (LoginThrottle).
 */
@ConfigurationProperties("financial-coach.rate-limit")
record RateLimitProperties(int login, int refresh, int logout, int loginAccountFailures) {
}
