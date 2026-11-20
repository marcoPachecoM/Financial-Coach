package com.financialcoach.auth;

import com.financialcoach.shared.web.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Temporary authentication: HTTP Basic against the users table.
 *
 * <p>When JWT login is implemented, only this class changes (httpBasic becomes a bearer-token
 * filter). Controllers keep receiving {@link AuthenticatedUser}.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationEntryPoint authenticationEntryPoint,
                                            AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless API: no cookies, so no CSRF token and no HTTP session.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/health/public").permitAll()
                        .requestMatchers("/api/v1/health/admin").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * Verifies existing hashes with the parameters stored in each hash (so the Argon2id hashes
     * created by the Python backend keep working) and creates new ones with Spring's defaults.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** 401 for missing or invalid credentials. Same response for every cause, so nothing leaks. */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ProblemResponseWriter problems) {
        return (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"financial-coach\"");
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication is required.");
        };
    }

    /** 403 when the caller is authenticated but lacks the required role. */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ProblemResponseWriter problems) {
        return (request, response, exception) -> problems.write(request, response,
                HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "Insufficient role permissions.");
    }
}
