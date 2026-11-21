package com.financialcoach.categorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns a category to a merchant when the user did not choose one.
 *
 * <p>Current pipeline: merchant cache, then the default category. The LLM step (and keyword
 * rules for the new categories) will be added here later; callers will not change.
 */
@Service
public class CategorizationService {

    static final Category DEFAULT_CATEGORY = Category.OTHER;

    private final MerchantCategoryRepository cache;

    CategorizationService(MerchantCategoryRepository cache) {
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public CategoryAssignment categorize(String merchantName) {
        String key = MerchantNames.normalize(merchantName);
        if (key.isEmpty()) {
            return new CategoryAssignment(DEFAULT_CATEGORY, CategorySource.RULE);
        }
        return cache.findById(key)
                .map(cached -> new CategoryAssignment(cached.getCategory(), cached.getSource()))
                .orElse(new CategoryAssignment(DEFAULT_CATEGORY, CategorySource.RULE));
    }
}
