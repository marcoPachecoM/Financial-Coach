package com.financialcoach.analytics;

import com.financialcoach.support.TestUsers.TestUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts accounts and transactions directly, so analytics tests do not depend on other endpoints. */
record AnalyticsTestData(JdbcTemplate jdbc) {

    UUID account(TestUser owner, String name, String monthlyBudget) {
        return jdbc.queryForObject(
                "INSERT INTO accounts (user_id, name, monthly_budget) VALUES (?, ?, ?) RETURNING id",
                UUID.class, owner.id(), name, monthlyBudget == null ? null : new BigDecimal(monthlyBudget));
    }

    void expense(TestUser owner, UUID accountId, String datetime, String amount, String category) {
        insert(owner, accountId, "EXPENSE", datetime, amount, category, false);
    }

    /** {@code amount} is the positive figure; it is stored negative. */
    void income(TestUser owner, UUID accountId, String datetime, String amount, String category) {
        insert(owner, accountId, "INCOME", datetime, "-" + amount, category, false);
    }

    void deletedExpense(TestUser owner, UUID accountId, String datetime, String amount, String category) {
        insert(owner, accountId, "EXPENSE", datetime, amount, category, true);
    }

    private void insert(TestUser owner, UUID accountId, String type, String datetime, String amount,
                        String category, boolean deleted) {
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, datetime, name, amount, category,
                                          source, category_source, deleted_at)
                VALUES (?, ?, ?, ?, 'Test', ?, ?, 'MANUAL', 'USER', CASE WHEN ? THEN now() END)
                """, owner.id(), accountId, type, LocalDateTime.parse(datetime), new BigDecimal(amount),
                category, deleted);
    }
}
