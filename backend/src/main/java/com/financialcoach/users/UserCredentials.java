package com.financialcoach.users;

import java.util.UUID;

/** What the authentication layer needs to know about a user. Never returned by the API. */
public record UserCredentials(UUID id, String email, String passwordHash, UserRole role, boolean active) {

    /** Keeps the password hash out of logs. */
    @Override
    public String toString() {
        return "UserCredentials[id=%s, email=%s, role=%s, active=%s]".formatted(id, email, role, active);
    }
}
