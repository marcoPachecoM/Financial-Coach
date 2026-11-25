package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The generator runs at every startup. On Cloud Run a deploy briefly has two instances (the old and
 * the new revision), so it may run twice at the same moment: each occurrence must still exist once.
 * Not {@code @Transactional}: each run commits its own transactions, so the data is removed by hand.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecurringConcurrencyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 5);

    @Autowired
    private RecurringTransactionService service;

    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private UUID userId;
    private UUID ruleId;

    @BeforeEach
    void createMonthlyRule() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (username, email, password_hash, role, is_active)
                VALUES ('recurring-race', 'recurring-race@example.test', 'x', 'CLIENT', true)
                RETURNING id
                """, UUID.class);
        UUID accountId = jdbc.queryForObject(
                "INSERT INTO accounts (user_id, name) VALUES (?, 'Card') RETURNING id", UUID.class, userId);
        // Started on Sep 5: the Oct 5, Nov 5 and Dec 5 occurrences are due.
        ruleId = jdbc.queryForObject("""
                INSERT INTO recurring_transactions (user_id, account_id, type, name, amount, category_group,
                                                    frequency, start_date, time_of_day, next_due_date)
                VALUES (?, ?, 'EXPENSE', 'Netflix', 199.00, 'Entertainment', 'MONTHLY', '2026-09-05', '10:00',
                        '2026-10-05')
                RETURNING id
                """, UUID.class, userId, accountId);
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbc.update("DELETE FROM users WHERE id = ?", userId); // accounts, rules and transactions go with it
    }

    @Test
    void twoGeneratorsAtTheSameTimeCreateEachOccurrenceOnce() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> run = () -> {
            start.await(10, TimeUnit.SECONDS);
            return service.generateDue(TODAY);
        };
        Future<Integer> first = executor.submit(run);
        Future<Integer> second = executor.submit(run);
        int processed = first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS);

        assertThat(processed).as("only one of them processes the rule").isEqualTo(1);
        assertThat(occurrences()).containsExactly(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5));

        // A later start the same day (a cold start, another deploy) finds nothing due.
        assertThat(service.generateDue(TODAY)).isZero();
        assertThat(occurrences()).hasSize(3);
    }

    private List<LocalDate> occurrences() {
        return jdbc.queryForList("""
                SELECT recurrence_date FROM transactions
                WHERE recurring_transaction_id = ? AND deleted_at IS NULL
                ORDER BY recurrence_date
                """, LocalDate.class, ruleId);
    }
}
