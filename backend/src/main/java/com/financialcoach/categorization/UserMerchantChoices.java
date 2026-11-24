package com.financialcoach.categorization;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** The user's corrections ({@code user_merchant_categories}, V13): normalized merchant → group. */
@Component
class UserMerchantChoices {

    private final NamedParameterJdbcTemplate jdbc;

    UserMerchantChoices(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, CategoryGroup> find(UUID userId, Collection<String> merchantKeys) {
        Map<String, CategoryGroup> choices = new HashMap<>();
        if (merchantKeys.isEmpty()) {
            return choices;
        }
        jdbc.query("""
                        SELECT merchant_name, category_group FROM user_merchant_categories
                        WHERE user_id = :userId AND merchant_name IN (:names)
                        """,
                new MapSqlParameterSource("userId", userId).addValue("names", merchantKeys),
                row -> {
                    choices.put(row.getString("merchant_name"), CategoryGroup.fromLabel(row.getString("category_group")));
                });
        return choices;
    }

    /** The latest choice wins. */
    void remember(UUID userId, String merchantKey, CategoryGroup group) {
        jdbc.update("""
                        INSERT INTO user_merchant_categories (user_id, merchant_name, category_group)
                        VALUES (:userId, :name, :group)
                        ON CONFLICT (user_id, merchant_name)
                        DO UPDATE SET category_group = EXCLUDED.category_group, updated_at = now()
                        """,
                new MapSqlParameterSource("userId", userId).addValue("name", merchantKey).addValue("group", group.label()));
    }
}
