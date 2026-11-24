package com.financialcoach.auth;

import com.financialcoach.auth.AccountSecurityResponses.PasswordReset;
import com.financialcoach.auth.AccountSecurityResponses.Registered;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.shared.error.TooManyRequestsException;
import com.financialcoach.shared.error.ValidationException;
import com.financialcoach.users.UserCredentials;
import com.financialcoach.users.UserProfile;
import com.financialcoach.users.UserService;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up and getting back into an account without an email service: recovery codes, password
 * change, and an admin reset as the last resort. Every change of password ends all sessions.
 * Audit log lines carry the user id and the client IP, never a password or a code.
 */
@Service
class AccountSecurityService {

    private static final Logger log = LoggerFactory.getLogger(AccountSecurityService.class);
    private static final String TEMPORARY_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TEMPORARY_LENGTH = 16;

    private final UserService userService;
    private final AuthService authService;
    private final AccountRecoveryChannel recovery;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle throttle;
    private final SecureRandom random = new SecureRandom();

    AccountSecurityService(UserService userService, AuthService authService, AccountRecoveryChannel recovery,
                           PasswordEncoder passwordEncoder, LoginThrottle throttle) {
        this.userService = userService;
        this.authService = authService;
        this.recovery = recovery;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
    }

    /** A new active CLIENT, signed in at once, with its recovery codes (shown only now). */
    @Transactional
    Registered register(RegisterRequest request, String ip) {
        PasswordPolicy.check("password", request.password(), request.email(), request.username());
        UserProfile user = userService.register(request.username(), request.email(),
                passwordEncoder.encode(request.password()));
        List<String> codes = recovery.issue(user.id());
        TokenPairResponse tokens = authService.issueFor(user.id(), user.role());
        log.info("Account created: user {} from {}", user.id(), ip);
        return new Registered(tokens.accessToken(), tokens.refreshToken(), codes);
    }

    /**
     * Forgot password. Failures count against the email like failed logins (LoginThrottle), and
     * every cause gets the same answer. The password rules are checked first, so a weak new
     * password does not use up the code.
     */
    @Transactional
    PasswordReset resetPassword(PasswordResetRequest request, String ip) {
        long wait = throttle.secondsUntilAllowed(request.email());
        if (wait > 0) {
            throw new TooManyRequestsException(wait,
                    "Too many failed attempts. Try again in %d seconds.".formatted(wait));
        }
        PasswordPolicy.check("new_password", request.newPassword(), request.email(), null);

        Optional<UserCredentials> found = userService.findCredentialsByEmail(request.email());
        if (found.isEmpty() || !found.get().active() || !recovery.consume(found.get().id(), request.recoveryCode())) {
            throttle.recordFailure(request.email());
            log.info("Password reset failed from {}", ip);
            throw new InvalidRecoveryException();
        }
        UUID userId = found.get().id();
        userService.changePasswordHash(userId, passwordEncoder.encode(request.newPassword()));
        int revoked = authService.revokeAllSessions(userId);
        throttle.recordSuccess(request.email());
        log.info("Password reset with a recovery code: user {} from {}; {} sessions ended", userId, ip, revoked);
        return new PasswordReset("Password changed. Sign in with the new one.", recovery.remaining(userId));
    }

    /** Ends every session, then returns a new pair so this one goes on. */
    @Transactional
    TokenPairResponse changePassword(UUID userId, ChangePasswordRequest request, String ip) {
        UserCredentials user = requireCurrentPassword(userId, request.currentPassword());
        UserProfile profile = userService.findProfile(userId).orElseThrow();
        PasswordPolicy.check("new_password", request.newPassword(), profile.email(), profile.username());
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ValidationException("new_password", "must be different from the current password");
        }
        userService.changePasswordHash(userId, passwordEncoder.encode(request.newPassword()));
        int revoked = authService.revokeAllSessions(userId);
        log.info("Password changed: user {} from {}; {} sessions ended", userId, ip, revoked);
        return authService.issueFor(userId, user.role());
    }

    @Transactional
    List<String> regenerateRecoveryCodes(UUID userId, String currentPassword, String ip) {
        requireCurrentPassword(userId, currentPassword);
        List<String> codes = recovery.issue(userId);
        log.info("Recovery codes regenerated: user {} from {}", userId, ip);
        return codes;
    }

    @Transactional(readOnly = true)
    long remainingRecoveryCodes(UUID userId) {
        return recovery.remaining(userId);
    }

    /**
     * Last resort when a user lost the password and the codes: a temporary password, shown once.
     * The user is found by email (ignoring case); an admin may learn whether it exists (404).
     */
    @Transactional
    String adminReset(UUID adminId, String email, String ip) {
        UUID userId = userService.findCredentialsByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."))
                .id();
        StringBuilder temporary = new StringBuilder();
        for (int i = 0; i < TEMPORARY_LENGTH; i++) {
            temporary.append(TEMPORARY_ALPHABET.charAt(random.nextInt(TEMPORARY_ALPHABET.length())));
        }
        userService.changePasswordHash(userId, passwordEncoder.encode(temporary));
        int revoked = authService.revokeAllSessions(userId);
        log.warn("Admin {} reset the password of user {} from {}; {} sessions ended", adminId, userId, ip, revoked);
        return temporary.toString();
    }

    private UserCredentials requireCurrentPassword(UUID userId, String currentPassword) {
        UserCredentials user = userService.findCredentials(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ValidationException("current_password", "is incorrect");
        }
        return user;
    }
}
