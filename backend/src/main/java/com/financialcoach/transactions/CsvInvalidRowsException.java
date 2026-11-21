package com.financialcoach.transactions;

import com.financialcoach.shared.error.ApiException;
import com.financialcoach.shared.error.ApiFieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 400 CSV_INVALID_ROWS: at least one row is invalid, so nothing was imported. */
class CsvInvalidRowsException extends ApiException {

    CsvInvalidRowsException(String detail, List<ApiFieldError> errors) {
        super(HttpStatus.BAD_REQUEST, "CSV_INVALID_ROWS", detail, errors);
    }
}
