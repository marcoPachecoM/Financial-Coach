package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountResponse;
import java.util.List;

/** {@code CsvUploadResponse} schema of openapi.yaml. */
record CsvUploadResponse(int recordsImported, int recordsDuplicated, List<AccountResponse> accountsCreated) {
}
