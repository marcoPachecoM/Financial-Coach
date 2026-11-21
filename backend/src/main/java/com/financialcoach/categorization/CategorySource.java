package com.financialcoach.categorization;

/** Who assigned a transaction's category ({@code transactions.category_source}). Internal, not exposed by the API. */
public enum CategorySource {
    /** Chosen by the user in the form or an edit. */
    USER,
    /** Came in the CSV file. */
    CSV,
    /** Assigned by the backend without AI: merchant rule or default category. */
    RULE,
    /** Assigned by the language model. */
    LLM
}
