package com.financialcoach.categorization;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns a category group to merchants when the user did not choose one. The free-text category
 * is never guessed. For each normalized merchant name, the first that knows wins:
 *
 * <ol>
 *   <li>the user's own corrections (they chose a group for that merchant before);
 *   <li>the shared merchant cache (classified once for every user);
 *   <li>the LLM, one batch for all the remaining merchants, saved to the shared cache;
 *   <li>the default group, Other.
 * </ol>
 *
 * Only normalized merchant names reach the LLM: no amounts, dates, accounts or user data.
 */
@Service
public class CategorizationService {

    static final CategoryGroup DEFAULT_GROUP = CategoryGroup.OTHER;
    private static final CategoryGroupAssignment DEFAULT = new CategoryGroupAssignment(DEFAULT_GROUP,
            CategoryGroupSource.RULE);

    private final MerchantCategoryRepository cache;
    private final UserMerchantChoices userChoices;
    private final MerchantClassifier classifier;

    CategorizationService(MerchantCategoryRepository cache, UserMerchantChoices userChoices,
                          MerchantClassifier classifier) {
        this.cache = cache;
        this.userChoices = userChoices;
        this.classifier = classifier;
    }

    /**
     * The normalized form of a merchant name ("NETFLIX.COM #123" becomes "netflix com"), used to
     * match transactions of the same merchant. Empty when nothing is left after normalizing.
     */
    public static String merchantKey(String merchantName) {
        return MerchantNames.normalize(merchantName);
    }

    /** Groups for many merchant names (a CSV import) with at most one round of LLM calls. */
    @Transactional
    public Categorizations categorizeAll(UUID userId, Collection<String> names) {
        Map<String, String> keyByName = new HashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (String name : names) {
            String key = MerchantNames.normalize(name);
            keyByName.put(name, key);
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }

        Map<String, CategoryGroupAssignment> byKey = new HashMap<>();
        userChoices.find(userId, keys)
                .forEach((key, group) -> byKey.put(key, new CategoryGroupAssignment(group, CategoryGroupSource.USER)));
        List<String> notChosen = keys.stream().filter(key -> !byKey.containsKey(key)).toList();
        cache.findAllById(notChosen).forEach(cached -> byKey.put(cached.getMerchantName(),
                new CategoryGroupAssignment(cached.getCategoryGroup(), cached.getSource())));

        List<String> unknown = keys.stream().filter(key -> !byKey.containsKey(key)).toList();
        if (!unknown.isEmpty()) {
            classifier.classify(unknown).forEach((key, group) -> {
                if (keys.contains(key)) {
                    cache.insertIfAbsent(key, group.label(), CategoryGroupSource.LLM.name());
                    byKey.put(key, new CategoryGroupAssignment(group, CategoryGroupSource.LLM));
                }
            });
        }

        Map<String, CategoryGroupAssignment> byName = new HashMap<>();
        Set<String> fallback = new HashSet<>();
        keyByName.forEach((name, key) -> {
            CategoryGroupAssignment assignment = byKey.get(key);
            if (assignment == null) {
                fallback.add(name);
                assignment = DEFAULT;
            }
            byName.put(name, assignment);
        });
        return new Categorizations(byName, fallback);
    }

    /** One merchant (a transaction typed in the form without a group). */
    @Transactional
    public CategoryGroupAssignment categorize(UUID userId, String merchantName) {
        return categorizeAll(userId, List.of(merchantName)).of(merchantName);
    }

    /**
     * Learns the group the user chose for a merchant (in the form or an edit): their next
     * transactions of that merchant without a group get it. Only for this user.
     */
    @Transactional
    public void rememberUserChoice(UUID userId, String merchantName, CategoryGroup group) {
        String key = MerchantNames.normalize(merchantName);
        if (!key.isEmpty()) {
            userChoices.remember(userId, key, group);
        }
    }
}
