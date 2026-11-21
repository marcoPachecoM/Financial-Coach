package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 401 with a specific code (INVALID_CREDENTIALS, INVALID_TOKEN, INVALID_REFRESH_TOKEN, ...). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, detail);
    }
}
