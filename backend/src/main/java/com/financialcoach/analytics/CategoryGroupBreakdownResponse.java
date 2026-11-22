package com.financialcoach.analytics;

import com.financialcoach.categorization.CategoryGroup;

/** {@code CategoryGroupBreakdown} schema of openapi.yaml. */
record CategoryGroupBreakdownResponse(CategoryGroup categoryGroup, String amount, String percentage, long count) {
}
