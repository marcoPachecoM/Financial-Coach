package com.financialcoach.auth;

import com.financialcoach.users.UserRole;
import java.util.UUID;

/**
 * The caller of a request, built from the access token (claims {@code sub} and {@code role}),
 * without a database query. Controllers receive it with {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(UUID id, UserRole role) {
}
