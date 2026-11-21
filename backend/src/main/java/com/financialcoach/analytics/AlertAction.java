package com.financialcoach.analytics;

/**
 * {@code AlertAction} of openapi.yaml: what the client can offer to fix the alert. The alert's
 * {@code data} has the ids the action needs.
 */
enum AlertAction {
    /** POST /recurring-transactions with data.transaction_id and data.frequency. */
    CREATE_RECURRING,
    /** PATCH /recurring-transactions/{data.recurring_transaction_id} with amount = data.charged_amount. */
    UPDATE_RECURRING_AMOUNT,
    /** PATCH /recurring-transactions/{data.recurring_transaction_id} with an end_date. */
    STOP_RECURRING
}
