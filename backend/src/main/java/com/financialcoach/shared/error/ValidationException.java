package com.financialcoach.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 400 VALIDATION_ERROR for rules that Bean Validation annotations cannot express. */
public class ValidationException extends ApiException {

    public ValidationException(String field, String message) {
        this(List.of(ApiFieldError.of(field, message)));
    }

    public ValidationException(List<ApiFieldError> errors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", describe(errors.size()), errors);
    }

    public ValidationException(String detail) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    public static String describe(int invalidFields) {
        return invalidFields == 1 ? "1 field is invalid." : invalidFields + " fields are invalid.";
    }
}
