package com.financialcoach.auth;

import com.financialcoach.shared.error.UnauthorizedException;
import com.financialcoach.users.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints of openapi.yaml. login/refresh/logout are public in SecurityConfig: refresh and
 * logout read the refresh token from the Authorization header themselves.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String BEARER_PREFIX = "bearer ";

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenPairResponse refresh(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.refresh(bearerToken(authorization));
    }

    @PostMapping("/logout")
    MessageResponse logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(bearerToken(authorization));
        return new MessageResponse("Logged out.");
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new MeResponse(authService.currentUser(user.id()));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication is required.");
        }
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || authorization.substring(BEARER_PREFIX.length()).isBlank()) {
            throw new UnauthorizedException("INVALID_TOKEN", "Expected 'Authorization: Bearer <refresh_token>'.");
        }
        return authorization.substring(BEARER_PREFIX.length()).strip();
    }

    record MessageResponse(String message) {
    }

    record MeResponse(UserProfile user) {
    }
}
