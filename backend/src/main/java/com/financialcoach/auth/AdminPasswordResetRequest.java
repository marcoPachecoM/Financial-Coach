package com.financialcoach.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /admin/users/password-reset}: the user is found by email, ignoring case. */
record AdminPasswordResetRequest(@NotBlank @Email @Size(max = 255) String email) {

    AdminPasswordResetRequest {
        email = email == null ? null : email.strip();
    }
}
