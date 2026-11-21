package com.financialcoach.categorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns a category group to a merchant when the user did not choose one. The free-text
 * category is never guessed.
 *
 * <p>Current pipeline: merchant cache, then the default group. The LLM step (and keyword
 * rules) will be added here later; callers will not change.
 */
@Service
public class CategorizationService {

    static final CategoryGroup DEFAULT_GROUP = CategoryGroup.OTHER;

    private final MerchantCategoryRepository cache;

    CategorizationService(MerchantCategoryRepository cache) {
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public CategoryGroupAssignment categorize(String merchantName) {
        String key = MerchantNames.normalize(merchantName);
        if (key.isEmpty()) {
            return new CategoryGroupAssignment(DEFAULT_GROUP, CategoryGroupSource.RULE);
        }
        return cache.findById(key)
                .map(cached -> new CategoryGroupAssignment(cached.getCategoryGroup(), cached.getSource()))
                .orElse(new CategoryGroupAssignment(DEFAULT_GROUP, CategoryGroupSource.RULE));
    }
}
