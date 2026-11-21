package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 409: the request clashes with existing data (e.g. a duplicate name). */
public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "CONFLICT", detail);
    }
}
