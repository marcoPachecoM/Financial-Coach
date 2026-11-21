package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountResponse;
import java.util.List;

/**
 * {@code CsvUploadResponse} schema of openapi.yaml. {@code recurring_matched}: imported rows
 * linked to a recurring transaction (they replace the occurrence created automatically).
 */
record CsvUploadResponse(int recordsImported, int recordsDuplicated, int recurringMatched,
                         List<AccountResponse> accountsCreated) {
}
