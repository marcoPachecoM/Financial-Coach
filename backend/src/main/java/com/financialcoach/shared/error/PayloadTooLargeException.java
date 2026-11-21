package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 413: the request body or uploaded file is larger than allowed. */
public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException(String detail) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", detail);
    }
}
