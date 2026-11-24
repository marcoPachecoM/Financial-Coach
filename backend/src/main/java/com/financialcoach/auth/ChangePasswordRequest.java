package com.financialcoach.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/change-password}. */
record ChangePasswordRequest(
        @NotEmpty @Size(max = 1024) String currentPassword,
        @NotEmpty @Size(max = 1024) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
