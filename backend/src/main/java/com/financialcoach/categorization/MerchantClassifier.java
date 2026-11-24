package com.financialcoach.categorization;

import java.util.Collection;
import java.util.Map;

/**
 * Guesses the category group of merchants nobody has classified yet. Receives normalized merchant
 * names only (MerchantNames.normalize: no digits, no amounts, no user data). Merchants it cannot
 * classify are left out of the answer; it never throws, so a failure only means "Other".
 */
public interface MerchantClassifier {

    Map<String, CategoryGroup> classify(Collection<String> merchantKeys);
}
