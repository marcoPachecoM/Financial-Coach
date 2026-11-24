package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two requests creating the same new tag at the same time. Not {@code @Transactional}: each side
 * needs its own committed transaction, so the data is removed by hand afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TagConcurrencyTest {

    @Autowired
    private TagService tagService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private UUID userId;

    @BeforeEach
    void createUser() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (username, email, password_hash, role, is_active)
                VALUES ('tag-race', 'tag-race@example.test', 'x', 'CLIENT', true)
                RETURNING id
                """, UUID.class);
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbc.update("DELETE FROM users WHERE id = ?", userId); // its tags go with it (ON DELETE CASCADE)
    }

    @Test
    void theSecondRequestWaitsAndGetsTheSameTag() throws Exception {
        TransactionTemplate inTransaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstCreatedIt = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);

        Future<UUID> first = executor.submit(() -> inTransaction.execute(status -> {
            UUID id = tagService.resolve(userId, List.of("Trip")).get("trip").getId();
            firstCreatedIt.countDown();
            await(commitFirst); // not committed yet: the second request cannot see the tag
            return id;
        }));
        assertThat(firstCreatedIt.await(10, TimeUnit.SECONDS)).isTrue();

        Future<UUID> second = executor.submit(() -> inTransaction.execute(
                status -> tagService.resolve(userId, List.of("trip")).get("trip").getId()));
        Thread.sleep(300); // the second insert is now blocked by the first one's row
        commitFirst.countDown();

        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(first.get(10, TimeUnit.SECONDS));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
