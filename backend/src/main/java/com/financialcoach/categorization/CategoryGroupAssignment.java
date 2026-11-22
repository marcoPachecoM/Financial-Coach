package com.financialcoach.categorization;

/** Result of automatic categorization: the group and who chose it. */
public record CategoryGroupAssignment(CategoryGroup group, CategoryGroupSource source) {
}
