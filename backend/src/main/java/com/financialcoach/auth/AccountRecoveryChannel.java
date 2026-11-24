package com.financialcoach.auth;

import java.util.List;
import java.util.UUID;

/**
 * How a user proves who they are when the password is forgotten. Today: recovery codes shown at
 * sign-up (no email service needed). An email channel (a reset link through SMTP) could implement
 * this later without changing the endpoints that use it.
 */
interface AccountRecoveryChannel {

    /** New secrets for the user, replacing the previous ones; returned once, never stored in clear. */
    List<String> issue(UUID userId);

    /** True, and the secret can never be used again, when it is one of the user's valid secrets. */
    boolean consume(UUID userId, String secret);

    /** Valid secrets the user still has. */
    long remaining(UUID userId);
}
