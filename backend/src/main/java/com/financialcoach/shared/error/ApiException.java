package com.financialcoach.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map to a specific HTTP response. Services throw subclasses;
 * {@code ApiExceptionHandler} turns them into Problem Details with the given {@code code}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiFieldError> errors;

    protected ApiException(HttpStatus status, String code, String detail, List<ApiFieldError> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    protected ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ApiFieldError> errors() {
        return errors;
    }
}
