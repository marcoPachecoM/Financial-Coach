package com.financialcoach.copilot;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.web.ProblemResponseWriter;
import com.financialcoach.shared.web.RateLimiter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits on POST /copilot/chat and /copilot/chat/stream, with one counter per user for both (not
 * per IP: the endpoints are authenticated, and people behind the same network share an IP). The per-minute limit stops loops and scripts; the daily
 * one caps what the LLM can cost. Exceeding them returns 429 RATE_LIMIT_EXCEEDED with Retry-After.
 *
 * <p>Runs after Spring Security, so the user is known; anonymous requests never get here (401).
 */
@Configuration
class CopilotRateLimitConfig implements WebMvcConfigurer {

    private final RateLimiter limiter;
    private final CopilotProperties properties;
    private final ProblemResponseWriter problems;

    CopilotRateLimitConfig(RateLimiter limiter, CopilotProperties properties, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (request.getDispatcherType() == DispatcherType.ASYNC) {
                    return true; // a streamed answer is dispatched again when it ends: count it once
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                    return true; // Spring Security already answers 401; nothing to count
                }
                String key = "copilot|" + user.id();

                long retryAfter = limiter.acquire(key, properties.requestsPerMinute(), Duration.ofMinutes(1));
                if (retryAfter > 0) {
                    return reject(request, response, retryAfter,
                            "Too many messages. Try again in %d seconds.".formatted(retryAfter));
                }
                retryAfter = limiter.acquire(key, properties.dailyLimit(), Duration.ofDays(1));
                if (retryAfter > 0) {
                    return reject(request, response, retryAfter,
                            "You reached the daily limit of %d copilot messages. It resets at midnight."
                                    .formatted(properties.dailyLimit()));
                }
                return true;
            }
        }).addPathPatterns(CopilotController.CHAT_PATH, CopilotController.STREAM_PATH);
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, long retryAfter, String detail)
            throws Exception {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", detail);
        return false;
    }
}
