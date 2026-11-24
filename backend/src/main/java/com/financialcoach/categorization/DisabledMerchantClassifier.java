package com.financialcoach.categorization;

import java.util.Collection;
import java.util.Map;

/** Used when {@code financial-coach.categorization.llm-enabled=false}: classifies nothing. */
class DisabledMerchantClassifier implements MerchantClassifier {

    @Override
    public Map<String, CategoryGroup> classify(Collection<String> merchantKeys) {
        return Map.of();
    }
}
