package com.financialcoach.auth;

import com.financialcoach.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Limits on the token endpoints (openapi.yaml): login slows down password guessing, refresh and
 * logout protect the token table. Exceeding them returns 429 RATE_LIMIT_EXCEEDED.
 *
 * <p>The client is identified by its IP. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy} so the real client IP is used instead of the proxy's.
 */
@Configuration
class RateLimitConfig implements WebMvcConfigurer {

    private final AuthRateLimiter limiter;
    private final RateLimitProperties limits;
    private final ProblemResponseWriter problems;

    RateLimitConfig(AuthRateLimiter limiter, RateLimitProperties limits, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.limits = limits;
        this.problems = problems;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(limit("login", limits.login())).addPathPatterns("/api/v1/auth/login");
        registry.addInterceptor(limit("refresh", limits.refresh())).addPathPatterns("/api/v1/auth/refresh");
        registry.addInterceptor(limit("logout", limits.logout())).addPathPatterns("/api/v1/auth/logout");
    }

    private HandlerInterceptor limit(String endpoint, int perMinute) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                long retryAfter = limiter.acquire(endpoint, request.getRemoteAddr(), perMinute);
                if (retryAfter == 0) {
                    return true;
                }
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
                problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Too many requests. Try again in %d seconds.".formatted(retryAfter));
                return false;
            }
        };
    }
}
