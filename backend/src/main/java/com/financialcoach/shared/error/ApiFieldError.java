package com.financialcoach.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of {@code errors} in a Problem Details response ({@code FieldError} in openapi.yaml).
 * {@code row} is only set for CSV import errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(Integer row, String field, String message) {

    public static ApiFieldError of(String field, String message) {
        return new ApiFieldError(null, field, message);
    }
}
