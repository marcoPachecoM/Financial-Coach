package com.financialcoach.auth;

import com.financialcoach.shared.web.ProblemResponseWriter;
import com.financialcoach.shared.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Limits on the auth endpoints (openapi.yaml): login and password reset slow down guessing,
 * sign-up slows down bots creating accounts, refresh and logout protect the token table, and the
 * signed-in security endpoints are limited per user. Exceeding them returns 429 RATE_LIMIT_EXCEEDED.
 *
 * <p>Public endpoints identify the client by its IP. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy} so the real client IP is used instead of the proxy's.
 */
@Configuration
class RateLimitConfig implements WebMvcConfigurer {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final ProblemResponseWriter problems;

    RateLimitConfig(RateLimiter limiter, RateLimitProperties limits, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.limits = limits;
        this.problems = problems;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(perIp("login", limits.login(), MINUTE)).addPathPatterns("/api/v1/auth/login");
        registry.addInterceptor(perIp("refresh", limits.refresh(), MINUTE)).addPathPatterns("/api/v1/auth/refresh");
        registry.addInterceptor(perIp("logout", limits.logout(), MINUTE)).addPathPatterns("/api/v1/auth/logout");
        registry.addInterceptor(perIp("register", limits.register(), Duration.ofHours(1)))
                .addPathPatterns("/api/v1/auth/register");
        registry.addInterceptor(perIp("password-reset", limits.passwordReset(), MINUTE))
                .addPathPatterns("/api/v1/auth/password-reset");
        registry.addInterceptor(perUser("account-security", limits.accountSecurity()))
                .addPathPatterns("/api/v1/auth/change-password", "/api/v1/auth/recovery-codes");
    }

    private HandlerInterceptor perIp(String endpoint, int limit, Duration window) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                return allow(request, response, endpoint + "|" + request.getRemoteAddr(), limit, window);
            }
        };
    }

    /** By user instead of IP: these endpoints need a signed-in user (Spring Security answers 401 otherwise). */
    private HandlerInterceptor perUser(String endpoint, int perMinute) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                    return true;
                }
                return allow(request, response, endpoint + "|" + user.id(), perMinute, MINUTE);
            }
        };
    }

    private boolean allow(HttpServletRequest request, HttpServletResponse response, String key, int limit,
                          Duration window) throws IOException {
        long retryAfter = limiter.acquire(key, limit, window);
        if (retryAfter == 0) {
            return true;
        }
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Too many requests. Try again in %d seconds.".formatted(retryAfter));
        return false;
    }
}
