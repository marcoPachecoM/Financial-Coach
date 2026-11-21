package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 404. Also used for resources owned by another user, so their existence is not revealed.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", detail);
    }
}
