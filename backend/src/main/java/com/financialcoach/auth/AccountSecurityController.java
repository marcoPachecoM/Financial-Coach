package com.financialcoach.auth;

import com.financialcoach.auth.AccountSecurityResponses.PasswordReset;
import com.financialcoach.auth.AccountSecurityResponses.RecoveryCodesIssued;
import com.financialcoach.auth.AccountSecurityResponses.RecoveryStatus;
import com.financialcoach.auth.AccountSecurityResponses.Registered;
import com.financialcoach.auth.AccountSecurityResponses.TemporaryPassword;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and account recovery endpoints (openapi.yaml). register and password-reset are public
 * (SecurityConfig); the admin reset needs the ADMIN role.
 */
@RestController
class AccountSecurityController {

    private final AccountSecurityService service;

    AccountSecurityController(AccountSecurityService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/auth/register")
    ResponseEntity<Registered> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(body, request.getRemoteAddr()));
    }

    @PostMapping("/api/v1/auth/password-reset")
    PasswordReset resetPassword(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        return service.resetPassword(body, request.getRemoteAddr());
    }

    @PostMapping("/api/v1/auth/change-password")
    TokenPairResponse changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        return service.changePassword(user.id(), body, request.getRemoteAddr());
    }

    @GetMapping("/api/v1/auth/recovery-codes")
    RecoveryStatus recoveryStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return new RecoveryStatus(service.remainingRecoveryCodes(user.id()));
    }

    @PostMapping("/api/v1/auth/recovery-codes")
    RecoveryCodesIssued regenerateRecoveryCodes(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody CurrentPasswordRequest body,
                                                HttpServletRequest request) {
        return new RecoveryCodesIssued(
                service.regenerateRecoveryCodes(user.id(), body.currentPassword(), request.getRemoteAddr()));
    }

    @PostMapping("/api/v1/admin/users/password-reset")
    TemporaryPassword adminReset(@AuthenticationPrincipal AuthenticatedUser admin,
                                 @Valid @RequestBody AdminPasswordResetRequest body, HttpServletRequest request) {
        return new TemporaryPassword(service.adminReset(admin.id(), body.email(), request.getRemoteAddr()));
    }
}
