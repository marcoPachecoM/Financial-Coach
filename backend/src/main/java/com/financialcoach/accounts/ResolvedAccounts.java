package com.financialcoach.accounts;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Result of {@link AccountService#resolveOrCreate}: the id of every requested account name and the
 * accounts that had to be created. Names are matched case-insensitively, like the
 * ux_accounts_user_name index.
 */
public record ResolvedAccounts(Map<String, UUID> idsByKey, List<AccountResponse> created) {

    public ResolvedAccounts {
        idsByKey = Map.copyOf(idsByKey);
        created = List.copyOf(created);
    }

    /** The id of the account with this name (any capitalization, surrounding spaces ignored). */
    public UUID idFor(String name) {
        UUID id = idsByKey.get(key(name));
        if (id == null) {
            throw new IllegalArgumentException("Account was not resolved: " + name);
        }
        return id;
    }

    static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
