package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 409: the request clashes with existing data (e.g. a duplicate name). */
public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        this("CONFLICT", detail);
    }

    /** With a more specific code than CONFLICT, e.g. ALREADY_RECURRING. */
    public ConflictException(String code, String detail) {
        super(HttpStatus.CONFLICT, code, detail);
    }
}
