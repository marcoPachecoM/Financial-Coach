package com.financialcoach.auth;

import com.financialcoach.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 400 INVALID_RECOVERY: the email and recovery code do not match. One message for every cause
 * (unknown email, wrong code, used code, inactive user), so it never reveals which emails exist.
 */
class InvalidRecoveryException extends ApiException {

    InvalidRecoveryException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_RECOVERY", "Invalid email or recovery code.");
    }
}
