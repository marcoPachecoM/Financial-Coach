package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountResponse;
import java.util.List;

/**
 * {@code CsvUploadResponse} schema of openapi.yaml. {@code recurring_matched}: imported rows
 * linked to a recurring transaction (they replace the occurrence created automatically).
 * {@code records_categorized}: imported rows without a group in the file that got one from the
 * user's corrections, the merchant cache or the LLM (the rest got Other).
 */
record CsvUploadResponse(int recordsImported, int recordsDuplicated, int recurringMatched, int recordsCategorized,
                         List<AccountResponse> accountsCreated) {
}
