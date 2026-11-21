package com.financialcoach.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * {@code LoginRequest} schema of openapi.yaml. The password limit stops huge inputs from making
 * Argon2 work on megabytes of data.
 */
record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotEmpty @Size(max = 1024) String password) {

    /** Surrounding spaces (common with mobile keyboards) are removed before validation. */
    LoginRequest {
        email = email == null ? null : email.strip();
    }

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
