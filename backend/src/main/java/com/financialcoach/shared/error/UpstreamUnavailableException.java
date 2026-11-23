package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 502: Exception for an external service (e.g. the LLM provider) answered without usable data. */
public class UpstreamUnavailableException extends ApiException {

    public UpstreamUnavailableException(String code, String detail) {
        super(HttpStatus.BAD_GATEWAY, code, detail);
    }
}
