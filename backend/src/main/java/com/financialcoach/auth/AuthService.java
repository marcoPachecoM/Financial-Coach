package com.financialcoach.auth;

import com.financialcoach.shared.error.UnauthorizedException;
import com.financialcoach.users.UserCredentials;
import com.financialcoach.users.UserProfile;
import com.financialcoach.users.UserRole;
import com.financialcoach.users.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh-token rotation with reuse detection, and logout.
 *
 * <p>Rotation: every refresh revokes the token used and returns a new pair. If a rotated token
 * is used again, someone has a copy of it (the legitimate client already has the newer one), so
 * every refresh token of the user is revoked and both parties must log in again.
 */
@Service
class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final TokenService tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Checked when the email does not exist, so the response takes as long as a wrong password. */
    private final String dummyHash;

    AuthService(UserService userService, TokenService tokens, RefreshTokenRepository refreshTokens,
                PasswordEncoder passwordEncoder, Clock clock) {
        this.userService = userService;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Unknown email, wrong password and inactive user all get the same answer. */
    @Transactional
    TokenPairResponse login(String email, String password) {
        Optional<UserCredentials> found = userService.findCredentialsByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            log.info("Login failed: unknown email");
            throw invalidCredentials();
        }
        UserCredentials user = found.get();
        if (!passwordEncoder.matches(password, user.passwordHash()) || !user.active()) {
            log.info("Login failed for user {}", user.id());
            throw invalidCredentials();
        }
        log.info("Login succeeded for user {}", user.id());
        return issue(user.id(), user.role()).pair();
    }

    /**
     * Reuse is reported as an error, but its effects (all sessions revoked) must be committed:
     * hence noRollbackFor.
     */
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    TokenPairResponse refresh(String rawToken) {
        Jwt jwt = tokens.decodeRefreshToken(rawToken);
        UUID userId = UUID.fromString(jwt.getSubject());
        RefreshToken stored = refreshTokens.findByJtiForUpdate(jwt.getId())
                .filter(token -> token.belongsTo(userId))
                .orElseThrow(AuthService::invalidRefreshToken);
        Instant now = clock.instant();

        if (stored.wasRotated()) {
            stored.markReuseDetected(now);
            int revoked = refreshTokens.revokeAllActive(userId, now);
            log.warn("Refresh token reuse detected for user {}; {} active tokens revoked", userId, revoked);
            throw new RefreshTokenReuseException();
        }
        if (!stored.isUsable(now)) {
            throw invalidRefreshToken();
        }
        UserProfile user = userService.findProfile(userId)
                .filter(UserProfile::isActive)
                .orElseThrow(AuthService::invalidRefreshToken);

        // The new access token carries the current role, so role changes apply from here on.
        Issued issued = issue(user.id(), user.role());
        stored.rotate(now, issued.refreshJti());
        return issued.pair();
    }

    /** Idempotent: an already revoked or unknown token still logs out successfully. */
    @Transactional
    void logout(String rawToken) {
        Jwt jwt = tokens.decodeRefreshToken(rawToken);
        refreshTokens.findByJtiForUpdate(jwt.getId()).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    UserProfile currentUser(UUID userId) {
        return userService.findProfile(userId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "The user no longer exists."));
    }

    private Issued issue(UUID userId, UserRole role) {
        TokenService.IssuedRefreshToken refresh = tokens.createRefreshToken(userId);
        refreshTokens.save(new RefreshToken(refresh.jti(), userId, refresh.expiresAt()));
        return new Issued(new TokenPairResponse(tokens.createAccessToken(userId, role), refresh.token()), refresh.jti());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
    }

    private static UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("INVALID_REFRESH_TOKEN", "The refresh token is no longer valid.");
    }

    private record Issued(TokenPairResponse pair, String refreshJti) {
    }
}
