package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 429 RATE_LIMIT_EXCEEDED thrown by a service; the response carries Retry-After. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds, String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", detail);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
