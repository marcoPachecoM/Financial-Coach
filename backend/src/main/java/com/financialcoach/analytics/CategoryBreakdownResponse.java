package com.financialcoach.analytics;

import com.financialcoach.categorization.Category;

/** {@code CategoryBreakdown} schema of openapi.yaml. */
record CategoryBreakdownResponse(Category category, String amount, String percentage, long count) {
}
