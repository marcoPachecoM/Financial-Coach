package com.financialcoach.shared.error;

import org.springframework.http.HttpStatus;

/** 503: a dependency (e.g. the LLM provider) is overloaded, over quota or too slow; retry later. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, detail);
    }
}
