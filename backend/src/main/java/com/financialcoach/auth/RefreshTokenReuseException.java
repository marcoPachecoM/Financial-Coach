package com.financialcoach.auth;

import com.financialcoach.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/** 401 REFRESH_TOKEN_REUSE. A separate class so AuthService can commit despite throwing it. */
class RefreshTokenReuseException extends ApiException {

    RefreshTokenReuseException() {
        super(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE",
                "Refresh token reuse detected. All sessions were closed; log in again.");
    }
}
