package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the tag names a user typed into their tags, creating the new ones. Names are cleaned
 * (spaces trimmed and collapsed, a leading "#" removed) and compared ignoring case, so "Work",
 * "work " and "#work" are one tag, spelled as it was first created.
 */
@Service
class TagService {

    static final int MAX_LENGTH = 40;
    static final int MAX_PER_TRANSACTION = 10;
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final TagRepository tags;
    private final AccountService accountService;

    TagService(TagRepository tags, AccountService accountService) {
        this.tags = tags;
        this.accountService = accountService;
    }

    /** Clean form of a typed name; empty when nothing is left. */
    static String clean(String name) {
        String cleaned = SPACES.matcher(name == null ? "" : name.strip()).replaceAll(" ");
        return cleaned.startsWith("#") ? cleaned.substring(1).strip() : cleaned;
    }

    static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    /**
     * Checks the names of one transaction (400 VALIDATION_ERROR under {@code field}) and returns them
     * cleaned, without repeats, in the order given.
     */
    static List<String> validate(String field, Collection<String> names) {
        Map<String, String> byKey = new LinkedHashMap<>();
        List<ApiFieldError> errors = new ArrayList<>();
        for (String name : names) {
            String cleaned = clean(name);
            if (cleaned.isEmpty()) {
                errors.add(ApiFieldError.of(field, "must not contain empty tags"));
            } else if (cleaned.length() > MAX_LENGTH) {
                errors.add(ApiFieldError.of(field, "each tag must have at most %d characters".formatted(MAX_LENGTH)));
            } else if (cleaned.contains(";")) {
                errors.add(ApiFieldError.of(field, "tags must not contain \";\""));
            } else {
                byKey.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            }
        }
        if (byKey.size() > MAX_PER_TRANSACTION) {
            errors.add(ApiFieldError.of(field, "at most %d tags per transaction".formatted(MAX_PER_TRANSACTION)));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(List.copyOf(new LinkedHashSet<>(errors)));
        }
        return List.copyOf(byKey.values());
    }

    /**
     * The user's tags for these names (already validated), creating the missing ones. Runs in the
     * caller's transaction. Returns a map from lower-case name to tag.
     */
    @Transactional
    Map<String, Tag> resolve(UUID userId, Collection<String> names) {
        Map<String, String> wanted = new LinkedHashMap<>();
        names.forEach(name -> wanted.putIfAbsent(key(name), clean(name)));
        Map<String, Tag> byKey = new LinkedHashMap<>();
        if (wanted.isEmpty()) {
            return byKey;
        }
        tags.findByKeys(userId, wanted.keySet()).forEach(tag -> byKey.put(tag.getName().toLowerCase(Locale.ROOT), tag));
        List<String> missing = wanted.keySet().stream().filter(key -> !byKey.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            // INSERT ... ON CONFLICT DO NOTHING rather than save(): two requests creating the same
            // tag at once would otherwise end with one of them failing on the unique index.
            missing.forEach(key -> tags.insertIfAbsent(userId, wanted.get(key)));
            tags.findByKeys(userId, missing).forEach(tag -> byKey.put(tag.getName().toLowerCase(Locale.ROOT), tag));
        }
        return byKey;
    }

    /** The tags of these names, in the given order (each must be in {@code resolved}). */
    static Set<Tag> pick(Map<String, Tag> resolved, Collection<String> names) {
        Set<Tag> picked = new LinkedHashSet<>();
        names.forEach(name -> picked.add(resolved.get(key(name))));
        return picked;
    }

    /** {@code GET /tags}: tags in use, most used first; only one account when {@code accountId} is given. */
    @Transactional(readOnly = true)
    List<TagUsage> usage(UUID userId, UUID accountId) {
        if (accountId == null) {
            return tags.usage(userId);
        }
        accountService.requireOwned(userId, accountId);
        return tags.usageInAccount(userId, accountId);
    }
}
