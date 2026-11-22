package com.financialcoach.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** {@code User} schema of openapi.yaml. Never contains the password hash. */
public record UserProfile(UUID id, String username, String email, UserRole role,
                          @JsonProperty("is_active") boolean isActive) {
}
