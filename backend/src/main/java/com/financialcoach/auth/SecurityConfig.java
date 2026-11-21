package com.financialcoach.auth;

import com.financialcoach.shared.web.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * JWT bearer authentication: every request except the public ones needs a valid access token
 * ({@code Authorization: Bearer <access_token>}), validated by the JwtDecoder of JwtConfig.
 */
@Configuration
class SecurityConfig {

    /** Public, and they never read an access token: login has none, refresh/logout get a refresh token. */
    private static final RequestMatcher TOKEN_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/logout"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationEntryPoint authenticationEntryPoint,
                                            AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless API: tokens travel in a header, not in cookies, so CSRF does not apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(AccessTokenAuthentication::from)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/health/public").permitAll()
                        .requestMatchers(TOKEN_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/health/admin").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * Ignores the Authorization header on the token endpoints. Otherwise a stale access token
     * sent to login would be rejected with 401, and the refresh token sent to /refresh would be
     * validated as an access token.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> TOKEN_ENDPOINTS.matches(request) ? null : standard.resolve(request);
    }

    /**
     * Verifies existing hashes with the parameters stored in each hash (so the Argon2id hashes
     * created by the Python backend keep working) and creates new ones with Spring's defaults.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * 401 without a token (UNAUTHORIZED) or with an invalid, expired or wrong-type token
     * (INVALID_TOKEN). The detail never says which check failed.
     */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ProblemResponseWriter problems) {
        return (request, response, exception) -> {
            if (exception instanceof OAuth2AuthenticationException) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "The access token is invalid or expired.");
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication is required.");
            }
        };
    }

    /** 403 when the caller is authenticated but lacks the required role. */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ProblemResponseWriter problems) {
        return (request, response, exception) -> problems.write(request, response,
                HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "Insufficient role permissions.");
    }
}
