package com.financialcoach.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financialcoach.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the Flyway migrations to a real PostgreSQL (Testcontainers) and checks that the
 * database itself enforces the business rules of the API contract.
 *
 * <p>Each test runs in a transaction that is rolled back, so tests do not see each other's data.
 * Rejections are asserted by constraint name, so a test cannot pass for the wrong reason.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MigrationsTest {

    private static final int EXPECTED_VERSIONED_MIGRATIONS = 10;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void createUserWithAccount() {
        userId = insertUser("alice");
        accountId = insertAccount(userId, "Debit card");
    }

    @Test
    void appliesAllVersionedMigrations() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                Integer.class);

        assertThat(applied).isEqualTo(EXPECTED_VERSIONED_MIGRATIONS);
    }

    @Test
    void createsAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "users", "refresh_tokens", "accounts", "category_groups",
                "import_batches", "transactions", "merchant_category_cache", "recurring_transactions",
                "tags", "transaction_tags", "category_budgets");
    }

    @Test
    void acceptsValidExpenseAndIncome() {
        assertThatCode(() -> {
            insertTransaction(userId, accountId, "EXPENSE", "100.00", "Food", "MANUAL", null);
            insertTransaction(userId, accountId, "INCOME", "-20000.00", "Salary", "MANUAL", null);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsIncomeWithPositiveAmount() {
        assertThatThrownBy(() -> insertTransaction(userId, accountId, "INCOME", "500.00", "Salary", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_amount_sign");
    }

    @Test
    void rejectsExpenseWithZeroAmount() {
        assertThatThrownBy(() -> insertTransaction(userId, accountId, "EXPENSE", "0.00", "Food", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_amount_sign");
    }

    @Test
    void rejectsDuplicateAccountNameIgnoringCase() {
        assertThatThrownBy(() -> insertAccount(userId, "debit CARD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_accounts_user_name");
    }

    @Test
    void allowsSameAccountNameForDifferentUsers() {
        UUID bob = insertUser("bob");

        assertThatCode(() -> insertAccount(bob, "Debit card")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTransactionInAnotherUsersAccount() {
        UUID bob = insertUser("bob");

        assertThatThrownBy(() -> insertTransaction(bob, accountId, "EXPENSE", "10.00", "Food", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_account");
    }

    @Test
    void rejectsUnknownCategoryGroup() {
        assertThatThrownBy(() -> insertTransaction(userId, accountId, "EXPENSE", "10.00", "Groceries", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_category_group");
    }

    /** V7: the group is the closed list; the category is free text and optional. */
    @Test
    void categoryIsFreeText() {
        insertTransaction(userId, accountId, "EXPENSE", "232.50", "Utilities", "MANUAL", null);
        jdbc.update("UPDATE transactions SET category = 'Gas' WHERE account_id = ?", accountId);

        assertThat(jdbc.queryForObject("SELECT category FROM transactions WHERE account_id = ?", String.class, accountId))
                .isEqualTo("Gas");
    }

    @Test
    void rejectsDuplicateFingerprintInSameAccount() {
        String fingerprint = "a".repeat(64);
        insertTransaction(userId, accountId, "EXPENSE", "10.00", "Food", "CSV", fingerprint);

        assertThatThrownBy(() -> insertTransaction(userId, accountId, "EXPENSE", "10.00", "Food", "CSV", fingerprint))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_transactions_account_fingerprint");
    }

    @Test
    void softDeletedRowStillBlocksReimport() {
        String fingerprint = "b".repeat(64);
        UUID id = insertTransaction(userId, accountId, "EXPENSE", "10.00", "Food", "CSV", fingerprint);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", id);

        assertThatThrownBy(() -> insertTransaction(userId, accountId, "EXPENSE", "10.00", "Food", "CSV", fingerprint))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_transactions_account_fingerprint");
    }

    @Test
    void rejectsCsvRowWithoutFingerprint() {
        assertThatThrownBy(() -> insertTransaction(userId, accountId, "EXPENSE", "10.00", "Food", "CSV", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_fingerprint");
    }

    /** V8: recurring transactions. */
    @Test
    void rejectsRecurringRuleWithWrongSign() {
        assertThatThrownBy(() -> insertRule(accountId, "INCOME", "199.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_recurring_transactions_amount_sign");
    }

    @Test
    void rejectsRecurrenceDateWithoutRule() {
        UUID id = insertTransaction(userId, accountId, "EXPENSE", "199.00", "Services", "MANUAL", null);

        assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET recurrence_date = '2026-01-05' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_recurrence");
    }

    @Test
    void onlyOneActiveTransactionPerOccurrence() {
        UUID rule = insertRule(accountId, "EXPENSE", "199.00");
        UUID first = insertTransaction(userId, accountId, "EXPENSE", "199.00", "Services", "RECURRING", null);
        UUID second = insertTransaction(userId, accountId, "EXPENSE", "219.00", "Services", "MANUAL", null);
        link(first, rule);

        assertThatThrownBy(() -> link(second, rule))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_transactions_recurrence");
    }

    /** A deleted occurrence does not count: the real charge can replace the generated one. */
    @Test
    void aDeletedOccurrenceCanBeReplaced() {
        UUID rule = insertRule(accountId, "EXPENSE", "199.00");
        UUID generated = insertTransaction(userId, accountId, "EXPENSE", "199.00", "Services", "RECURRING", null);
        UUID real = insertTransaction(userId, accountId, "EXPENSE", "219.00", "Services", "MANUAL", null);
        link(generated, rule);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", generated);

        assertThatCode(() -> link(real, rule)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRuleOfAnotherAccount() {
        UUID cash = insertAccount(userId, "Cash");
        UUID rule = insertRule(cash, "EXPENSE", "199.00");
        UUID transaction = insertTransaction(userId, accountId, "EXPENSE", "199.00", "Services", "MANUAL", null);

        assertThatThrownBy(() -> link(transaction, rule))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_recurring_transaction");
    }

    @Test
    void deletingTheAccountRemovesRulesAndTheirTransactions() {
        UUID rule = insertRule(accountId, "EXPENSE", "199.00");
        link(insertTransaction(userId, accountId, "EXPENSE", "199.00", "Services", "RECURRING", null), rule);

        assertThatCode(() -> jdbc.update("DELETE FROM accounts WHERE id = ?", accountId)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recurring_transactions", Integer.class)).isZero();
    }

    @Test
    void rejectsNegativeMonthlyBudget() {
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE accounts SET monthly_budget = -1 WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_monthly_budget");
    }

    private UUID insertUser(String username) {
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password_hash, role)
                VALUES (?, ?, 'not-a-real-hash', 'CLIENT')
                RETURNING id
                """, UUID.class, username, username + "@example.test");
    }

    private UUID insertAccount(UUID owner, String name) {
        return jdbc.queryForObject("""
                INSERT INTO accounts (user_id, name)
                VALUES (?, ?)
                RETURNING id
                """, UUID.class, owner, name);
    }

    private UUID insertRule(UUID account, String type, String amount) {
        return jdbc.queryForObject("""
                INSERT INTO recurring_transactions
                    (user_id, account_id, type, name, amount, category_group, frequency, start_date, time_of_day)
                VALUES (?, ?, ?, 'Netflix', ?, 'Services', 'MONTHLY', '2026-01-05', '12:00')
                RETURNING id
                """, UUID.class, userId, account, type, new BigDecimal(amount));
    }

    private void link(UUID transaction, UUID rule) {
        jdbc.update("UPDATE transactions SET recurring_transaction_id = ?, recurrence_date = '2026-01-05' WHERE id = ?",
                rule, transaction);
    }

    private UUID insertTransaction(UUID owner, UUID account, String type, String amount,
                                   String categoryGroup, String source, String fingerprint) {
        String groupSource = "CSV".equals(source) ? "CSV" : "USER";
        return jdbc.queryForObject("""
                INSERT INTO transactions
                    (user_id, account_id, type, datetime, name, amount, category_group,
                     source, category_group_source, fingerprint)
                VALUES (?, ?, ?, ?, 'La tiendita', ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class,
                owner, account, type, LocalDateTime.of(2026, 1, 2, 13, 35),
                new BigDecimal(amount), categoryGroup, source, groupSource, fingerprint);
    }
}
