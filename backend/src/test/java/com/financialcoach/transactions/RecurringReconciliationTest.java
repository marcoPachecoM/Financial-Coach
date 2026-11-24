package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** CSV rows that are occurrences of a recurring transaction. "Today" is 2026-11-21. */
@Import(FixedClockConfiguration.class)
class RecurringReconciliationTest extends ApiIntegrationTest {

    private static final String HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes\n";

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecurringTransactionService service;

    @Autowired
    private EntityManager entityManager;

    private TestUser alice;
    private UUID card;

    @BeforeEach
    void createRule() throws Exception {
        clock.setToday(FixedClockConfiguration.TODAY);
        alice = users.create("alice", UserRole.CLIENT, true);
        card = jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Debit card') RETURNING id",
                UUID.class, alice.id());
        // Netflix every 5th since September: September (typed), October and November (generated).
        mvc.perform(post("/api/v1/transactions").with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "EXPENSE", "datetime": "2026-09-05T12:30", "name": "Netflix", "amount": "199",
                         "category_group": "Services", "account_id": "%s", "tags": ["subscriptions"],
                         "recurrence": {"frequency": "MONTHLY"}}
                        """.formatted(card)))
                .andExpect(status().isCreated());
    }

    @Test
    void theRealChargeReplacesTheGeneratedOne() throws Exception {
        upload("Expense,2026-11-06 03:10:00,03:10,NETFLIX.COM 866-579,-219.00,Services,,Debit card,\n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_imported").value(1))
                .andExpect(jsonPath("$.recurring_matched").value(1));

        assertThat(activeNetflix()).containsExactly(
                "2026-09-05 Netflix 199.00 MANUAL",
                "2026-10-05 Netflix 199.00 RECURRING",
                "2026-11-05 NETFLIX.COM 866-579 219.00 CSV");
        // The generated November occurrence is soft-deleted, not removed.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE source = 'RECURRING' AND deleted_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theRealChargeKeepsTheTagsOfTheGeneratedOne() throws Exception {
        upload("Expense,2026-11-06 03:10:00,03:10,NETFLIX.COM 866-579,-219.00,Services,,Debit card,\n")
                .andExpect(jsonPath("$.recurring_matched").value(1));

        syncDatabase();
        assertThat(jdbc.queryForList("""
                SELECT g.name FROM transactions t
                JOIN transaction_tags tt ON tt.transaction_id = t.id JOIN tags g ON g.id = tt.tag_id
                WHERE t.source = 'CSV' AND t.deleted_at IS NULL
                """, String.class)).containsExactly("subscriptions");
    }

    @Test
    void aChargeBeforeItsDueDateIsNotCreatedAgain() throws Exception {
        upload("Expense,2026-12-03 10:00:00,10:00,Netflix,199.00,Services,,Debit card,\n")
                .andExpect(jsonPath("$.recurring_matched").value(1));

        service.generateDue(LocalDate.of(2026, 12, 6));

        assertThat(activeNetflix()).last().isEqualTo("2026-12-05 Netflix 199.00 CSV");
        assertThat(activeNetflix()).hasSize(4);
    }

    @Test
    void aSecondChargeForTheSameOccurrenceStaysUnlinked() throws Exception {
        upload("""
                Expense,2026-11-05 10:00:00,10:00,Netflix,199.00,Services,,Debit card,
                Expense,2026-11-07 10:00:00,10:00,Netflix,199.00,Services,,Debit card,
                """)
                .andExpect(jsonPath("$.records_imported").value(2))
                .andExpect(jsonPath("$.recurring_matched").value(1));

        syncDatabase();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM transactions
                WHERE source = 'CSV' AND recurring_transaction_id IS NULL AND datetime = '2026-11-07 10:00:00'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void otherMerchantsAndFarDatesAreNotLinked() throws Exception {
        upload("""
                Expense,2026-11-05 10:00:00,10:00,Netflixy,199.00,Services,,Debit card,
                Expense,2026-11-05 10:00:00,10:00,Spotify,129.00,Services,,Debit card,
                Expense,2026-11-15 10:00:00,10:00,Netflix,199.00,Services,,Debit card,
                Income,2026-11-05 10:00:00,10:00,Netflix,-199.00,Services,,Debit card,
                """)
                .andExpect(jsonPath("$.records_imported").value(4))
                .andExpect(jsonPath("$.recurring_matched").value(0));
    }

    private ResultActions upload(String rows) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bank.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/v1/transactions/upload-csv").file(file).with(as(alice)));
    }

    /** "recurrence_date name amount source" of the active transactions linked to a rule. */
    private List<String> activeNetflix() {
        syncDatabase();
        return jdbc.queryForList("""
                SELECT recurrence_date || ' ' || name || ' ' || amount || ' ' || source FROM transactions
                WHERE account_id = ? AND recurring_transaction_id IS NOT NULL AND deleted_at IS NULL
                ORDER BY recurrence_date
                """, String.class, card);
    }

    private void syncDatabase() {
        entityManager.flush();
        entityManager.clear();
    }
}
