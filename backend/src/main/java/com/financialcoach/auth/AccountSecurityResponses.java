package com.financialcoach.auth;

import java.util.List;

/** Response bodies of the sign-up and recovery endpoints (openapi.yaml). */
final class AccountSecurityResponses {

    private AccountSecurityResponses() {
    }

    /** Signed in right away; the codes are shown once. */
    record Registered(String accessToken, String refreshToken, List<String> recoveryCodes) {
        @Override
        public String toString() {
            return "Registered[***]";
        }
    }

    record PasswordReset(String message, long recoveryCodesRemaining) {
    }

    record RecoveryCodesIssued(List<String> recoveryCodes) {
        @Override
        public String toString() {
            return "RecoveryCodesIssued[***]";
        }
    }

    record RecoveryStatus(long remaining) {
    }

    record TemporaryPassword(String temporaryPassword) {
        @Override
        public String toString() {
            return "TemporaryPassword[***]";
        }
    }
}
