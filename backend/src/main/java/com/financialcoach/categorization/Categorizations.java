package com.financialcoach.categorization;

import java.util.Map;
import java.util.Set;

/**
 * Result of {@link CategorizationService#categorizeAll}: a group for every name given, and which
 * names fell back to the default group because nobody (user, cache or LLM) could classify them.
 */
public record Categorizations(Map<String, CategoryGroupAssignment> byName, Set<String> fallbackNames) {

    public CategoryGroupAssignment of(String name) {
        return byName.get(name);
    }

    /** False when the group came from the user's corrections, the cache or the LLM. */
    public boolean isFallback(String name) {
        return fallbackNames.contains(name);
    }
}
