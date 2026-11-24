package com.financialcoach.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/password-reset}: proves the account with one recovery code. */
record PasswordResetRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String recoveryCode,
        @NotEmpty @Size(max = 1024) String newPassword) {

    PasswordResetRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "PasswordResetRequest[email=%s]".formatted(email);
    }
}
