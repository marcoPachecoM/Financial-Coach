package com.financialcoach.transactions;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.web.ProblemResponseWriter;
import com.financialcoach.shared.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Per-user limit on the CSV endpoints, which read or write a whole file at once: an export of every
 * account, or an import of up to 1 MB, in a loop would load the database. Exceeding it returns
 * 429 RATE_LIMIT_EXCEEDED with Retry-After. Runs after Spring Security, so the user is known.
 */
@Configuration
class CsvRateLimitConfig implements WebMvcConfigurer {

    static final String EXPORT_PATH = "/api/v1/transactions/export";
    static final String UPLOAD_PATH = "/api/v1/transactions/upload-csv";

    private final RateLimiter limiter;
    private final ProblemResponseWriter problems;
    private final int perMinute;

    CsvRateLimitConfig(RateLimiter limiter, ProblemResponseWriter problems,
                       @Value("${financial-coach.rate-limit.csv:10}") int perMinute) {
        this.limiter = limiter;
        this.problems = problems;
        this.perMinute = perMinute;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(limit("csv-export")).addPathPatterns(EXPORT_PATH);
        registry.addInterceptor(limit("csv-upload")).addPathPatterns(UPLOAD_PATH);
    }

    private HandlerInterceptor limit(String endpoint) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                    return true; // Spring Security already answers 401
                }
                long retryAfter = limiter.acquire(endpoint + "|" + user.id(), perMinute, Duration.ofMinutes(1));
                if (retryAfter == 0) {
                    return true;
                }
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
                problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Too many CSV requests. Try again in %d seconds.".formatted(retryAfter));
                return false;
            }
        };
    }
}
