package com.financialcoach.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/recovery-codes}: generating new codes asks for the password again. */
record CurrentPasswordRequest(@NotEmpty @Size(max = 1024) String currentPassword) {

    @Override
    public String toString() {
        return "CurrentPasswordRequest[***]";
    }
}
