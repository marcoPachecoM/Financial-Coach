package com.financialcoach.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/register}. The password rules live in PasswordPolicy. */
record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotEmpty @Size(max = 1024) String password) {

    RegisterRequest {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip();
    }

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[username=%s, email=%s]".formatted(username, email);
    }
}
