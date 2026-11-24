package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Recurring transactions: creation, automatic occurrences, editing. "Today" is 2026-11-21. */
@Import(FixedClockConfiguration.class)
class RecurringTransactionsTest extends ApiIntegrationTest {

    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String RECURRING = "/api/v1/recurring-transactions";

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecurringTransactionService service;

    @Autowired
    private EntityManager entityManager;

    private TestUser alice;
    private TestUser bob;
    private UUID aliceCard;
    private UUID aliceCash;

    @BeforeEach
    void createUsersAndAccounts() {
        clock.setToday(FixedClockConfiguration.TODAY);
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        aliceCard = insertAccount(alice, "Debit card");
        aliceCash = insertAccount(alice, "Cash");
    }

    @Nested
    class Create {

        @Test
        void createsTheRuleAndLinksTheTransaction() throws Exception {
            String transactionId = createNetflix("2026-11-05T12:30", "\"frequency\": \"MONTHLY\"")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recurring_transaction_id").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String ruleId = JsonPath.read(transactionId, "$.recurring_transaction_id");

            mvc.perform(get(RECURRING).param("account_id", aliceCard.toString()).with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(ruleId))
                    .andExpect(jsonPath("$[0].account_id").value(aliceCard.toString()))
                    .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                    .andExpect(jsonPath("$[0].name").value("Netflix"))
                    .andExpect(jsonPath("$[0].amount").value("199.00"))
                    .andExpect(jsonPath("$[0].category_group").value("Services"))
                    .andExpect(jsonPath("$[0].category").value("Streaming"))
                    .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                    .andExpect(jsonPath("$[0].start_date").value("2026-11-05"))
                    .andExpect(jsonPath("$[0].end_date").value(nullValue()))
                    .andExpect(jsonPath("$[0].next_due_date").value("2026-12-05"));
            assertThat(netflix()).hasSize(1);
        }

        @Test
        void createsTheOccurrencesAlreadyDue() throws Exception {
            createNetflix("2026-09-05T12:30", "\"frequency\": \"MONTHLY\"").andExpect(status().isCreated());

            List<Map<String, Object>> rows = netflix();
            assertThat(rows).extracting(row -> row.get("datetime").toString())
                    .containsExactly("2026-09-05 12:30:00.0", "2026-10-05 12:30:00.0", "2026-11-05 12:30:00.0");
            assertThat(rows).extracting(row -> row.get("source"))
                    .containsExactly("MANUAL", "RECURRING", "RECURRING");
            assertThat(rows).extracting(row -> row.get("recurrence_date").toString())
                    .containsExactly("2026-09-05", "2026-10-05", "2026-11-05");
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("amount").toString()).isEqualTo("199.00");
                assertThat(row.get("category_group")).isEqualTo("Services");
                assertThat(row.get("category")).isEqualTo("Streaming");
            });
        }

        @Test
        void endOfMonthDoesNotDrift() throws Exception {
            clock.setToday(LocalDate.of(2026, 4, 1));
            createNetflix("2026-01-31T08:00", "\"frequency\": \"MONTHLY\"").andExpect(status().isCreated());

            assertThat(netflix()).extracting(row -> row.get("recurrence_date").toString())
                    .containsExactly("2026-01-31", "2026-02-28", "2026-03-31");
        }

        @Test
        void endDateStopsTheOccurrences() throws Exception {
            createNetflix("2026-09-05T12:30", "\"frequency\": \"MONTHLY\", \"end_date\": \"2026-10-10\"")
                    .andExpect(status().isCreated());

            assertThat(netflix()).hasSize(2);
            mvc.perform(get(RECURRING).param("account_id", aliceCard.toString()).with(as(alice)))
                    .andExpect(jsonPath("$[0].end_date").value("2026-10-10"))
                    .andExpect(jsonPath("$[0].next_due_date").value(nullValue()));
        }

        @Test
        void linksAnExistingChargeInsteadOfCreatingAnother() throws Exception {
            // The real October charge was entered two days after its due date.
            createTransaction(alice, aliceCard, "NETFLIX.COM 866-579", "2026-10-07T09:00", "199");

            createNetflix("2026-09-05T12:30", "\"frequency\": \"MONTHLY\"").andExpect(status().isCreated());

            List<Map<String, Object>> rows = transactionsOf(aliceCard);
            assertThat(rows).extracting(row -> row.get("name") + "@" + row.get("recurrence_date") + "/" + row.get("source"))
                    .containsExactly(
                            "Netflix@2026-09-05/MANUAL",
                            "NETFLIX.COM 866-579@2026-10-05/MANUAL",
                            "Netflix@2026-11-05/RECURRING");
        }

        @Test
        void makesAnExistingTransactionRecurring() throws Exception {
            String id = createTransaction(alice, aliceCard, "Spotify", "2026-11-10T07:00", "129");

            mvc.perform(post(RECURRING).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transaction_id\": \"%s\", \"frequency\": \"MONTHLY\"}".formatted(id)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern(".*/api/v1/recurring-transactions/[0-9a-f-]{36}$")))
                    .andExpect(jsonPath("$.name").value("Spotify"))
                    .andExpect(jsonPath("$.next_due_date").value("2026-12-10"));

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(jsonPath("$.recurring_transaction_id").isNotEmpty());
        }

        @Test
        void aTransactionCanOnlyBelongToOneRule() throws Exception {
            String body = createNetflix("2026-11-05T12:30", "\"frequency\": \"MONTHLY\"")
                    .andReturn().getResponse().getContentAsString();
            String id = JsonPath.read(body, "$.id");

            mvc.perform(post(RECURRING).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transaction_id\": \"%s\", \"frequency\": \"WEEKLY\"}".formatted(id)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_RECURRING"));
        }

        @Test
        void rejectsEndDateBeforeTheTransaction() throws Exception {
            createNetflix("2026-11-05T12:30", "\"frequency\": \"MONTHLY\", \"end_date\": \"2026-11-04\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("end_date"));
        }

        @Test
        void rejectsUnknownFrequencyAndMissingFrequency() throws Exception {
            createNetflix("2026-11-05T12:30", "\"frequency\": \"DAILY\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            createNetflix("2026-11-05T12:30", "\"end_date\": \"2026-12-31\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void anotherUsersTransactionCannotBeMadeRecurring() throws Exception {
            String id = createTransaction(alice, aliceCard, "Spotify", "2026-11-10T07:00", "129");

            mvc.perform(post(RECURRING).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transaction_id\": \"%s\", \"frequency\": \"MONTHLY\"}".formatted(id)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Generator {

        @Test
        void createsOnlyWhatIsDueAndIsIdempotent() throws Exception {
            createNetflix("2026-11-05T12:30", "\"frequency\": \"MONTHLY\"").andExpect(status().isCreated());

            assertThat(service.generateDue(LocalDate.of(2026, 12, 4))).isZero();
            assertThat(netflix()).hasSize(1);

            assertThat(service.generateDue(LocalDate.of(2026, 12, 5))).isEqualTo(1);
            assertThat(service.generateDue(LocalDate.of(2026, 12, 5))).isZero();
            assertThat(netflix()).extracting(row -> row.get("recurrence_date").toString())
                    .containsExactly("2026-11-05", "2026-12-05");
        }

        @Test
        void weeklyRuleCatchesUpAfterAPause() throws Exception {
            createNetflix("2026-11-20T12:30", "\"frequency\": \"WEEKLY\"").andExpect(status().isCreated());

            service.generateDue(LocalDate.of(2026, 12, 12));

            assertThat(netflix()).extracting(row -> row.get("recurrence_date").toString())
                    .containsExactly("2026-11-20", "2026-11-27", "2026-12-04", "2026-12-11");
        }

        @Test
        void occurrencesCopyTheTagsOfTheLatestOne() throws Exception {
            String body = mvc.perform(post(TRANSACTIONS).with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("""
                            {"type": "EXPENSE", "datetime": "2026-09-05T12:30", "name": "Netflix", "amount": "199",
                             "category_group": "Services", "account_id": "%s", "tags": ["subscriptions"],
                             "recurrence": {"frequency": "MONTHLY"}}
                            """.formatted(aliceCard)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<List<String>>read(body, "$.tags")).containsExactly("subscriptions");
            assertThat(tagsByOccurrence()).containsExactly(
                    "2026-09-05 subscriptions", "2026-10-05 subscriptions", "2026-11-05 subscriptions");

            // Retagging the latest occurrence changes the next ones.
            String november = jdbc.queryForObject(
                    "SELECT id FROM transactions WHERE recurrence_date = '2026-11-05'", String.class);
            mvc.perform(patch(TRANSACTIONS + "/" + november).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tags\": [\"subscriptions\", \"streaming\"]}"))
                    .andExpect(status().isOk());
            service.generateDue(LocalDate.of(2026, 12, 5));

            assertThat(tagsByOccurrence()).last().isEqualTo("2026-12-05 streaming,subscriptions");
        }

        @Test
        void aDeletedOccurrenceIsNotCreatedAgain() throws Exception {
            createNetflix("2026-09-05T12:30", "\"frequency\": \"MONTHLY\"").andExpect(status().isCreated());
            String october = jdbc.queryForObject(
                    "SELECT id FROM transactions WHERE recurrence_date = '2026-10-05'", String.class);

            mvc.perform(delete(TRANSACTIONS + "/" + october).with(as(alice))).andExpect(status().isNoContent());
            service.generateDue(LocalDate.of(2026, 11, 21));

            assertThat(netflix()).extracting(row -> row.get("recurrence_date").toString())
                    .containsExactly("2026-09-05", "2026-11-05");
        }
    }

    @Nested
    class Update {

        private String ruleId;

        @BeforeEach
        void createRule() throws Exception {
            String body = createNetflix("2026-11-05T12:30", "\"frequency\": \"MONTHLY\"")
                    .andReturn().getResponse().getContentAsString();
            ruleId = JsonPath.read(body, "$.recurring_transaction_id");
        }

        @Test
        void changesOnlyTheNextOccurrences() throws Exception {
            patchRule("{\"amount\": \"219\", \"name\": \"Netflix Premium\", \"category\": null}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value("219.00"))
                    .andExpect(jsonPath("$.name").value("Netflix Premium"))
                    .andExpect(jsonPath("$.category").value(nullValue()));

            service.generateDue(LocalDate.of(2026, 12, 5));

            assertThat(transactionsOf(aliceCard)).extracting(row -> row.get("name") + " " + row.get("amount"))
                    .containsExactly("Netflix 199.00", "Netflix Premium 219.00");
        }

        @Test
        void stopsAndResumes() throws Exception {
            patchRule("{\"end_date\": \"2026-11-21\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.end_date").value("2026-11-21"))
                    .andExpect(jsonPath("$.next_due_date").value(nullValue()));
            service.generateDue(LocalDate.of(2026, 12, 5));
            assertThat(netflix()).hasSize(1);

            // Resumed on 2026-12-20: the 2026-12-05 occurrence, skipped while stopped, is not created.
            clock.setToday(LocalDate.of(2026, 12, 20));
            patchRule("{\"end_date\": null}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next_due_date").value("2027-01-05"));
        }

        @Test
        void rejectsAWrongSignAndAnEndBeforeTheStart() throws Exception {
            patchRule("{\"amount\": \"-199\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
            patchRule("{\"end_date\": \"2026-11-01\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("end_date"));
            patchRule("{}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void deletingTheRuleKeepsItsTransactions() throws Exception {
            service.generateDue(LocalDate.of(2026, 12, 5));
            String december = jdbc.queryForObject(
                    "SELECT id FROM transactions WHERE recurrence_date = '2026-12-05'", String.class);
            mvc.perform(delete(TRANSACTIONS + "/" + december).with(as(alice))).andExpect(status().isNoContent());

            mvc.perform(delete(RECURRING + "/" + ruleId).with(as(alice))).andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM recurring_transactions", Integer.class)).isZero();
            // Both stay (one of them deleted), unlinked.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM transactions WHERE name = 'Netflix' AND recurring_transaction_id IS NULL",
                    Integer.class)).isEqualTo(2);
        }

        @Test
        void movingATransactionToAnotherAccountUnlinksIt() throws Exception {
            String id = jdbc.queryForObject("SELECT id FROM transactions WHERE name = 'Netflix'", String.class);

            mvc.perform(patch(TRANSACTIONS + "/" + id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"account_id\": \"%s\"}".formatted(aliceCash)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recurring_transaction_id").value(nullValue()));
        }

        @Test
        void anotherUsersRuleIsNotFound() throws Exception {
            mvc.perform(get(RECURRING + "/" + ruleId).with(as(bob))).andExpect(status().isNotFound());
            mvc.perform(delete(RECURRING + "/" + ruleId).with(as(bob))).andExpect(status().isNotFound());
            mvc.perform(get(RECURRING).param("account_id", aliceCard.toString()).with(as(bob)))
                    .andExpect(status().isNotFound());
        }

        private ResultActions patchRule(String body) throws Exception {
            return mvc.perform(patch(RECURRING + "/" + ruleId).with(as(alice))
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }

    private ResultActions createNetflix(String datetime, String recurrence) throws Exception {
        return mvc.perform(post(TRANSACTIONS).with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "type": "EXPENSE",
                  "datetime": "%s",
                  "name": "Netflix",
                  "amount": "199",
                  "category_group": "Services",
                  "category": "Streaming",
                  "account_id": "%s",
                  "recurrence": { %s }
                }
                """.formatted(datetime, aliceCard, recurrence)));
    }

    private String createTransaction(TestUser user, UUID accountId, String name, String datetime, String amount)
            throws Exception {
        String body = mvc.perform(post(TRANSACTIONS).with(as(user)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "EXPENSE", "datetime": "%s", "name": "%s", "amount": "%s",
                         "category_group": "Services", "account_id": "%s"}
                        """.formatted(datetime, name, amount, accountId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Active Netflix transactions, oldest first. */
    private List<Map<String, Object>> netflix() {
        syncDatabase();
        return jdbc.queryForList("""
                SELECT * FROM transactions WHERE name = 'Netflix' AND deleted_at IS NULL ORDER BY datetime
                """);
    }

    /** "recurrence_date tag,tag" of each active Netflix occurrence, oldest first. */
    private List<String> tagsByOccurrence() {
        syncDatabase();
        return jdbc.queryForList("""
                SELECT t.recurrence_date || ' ' || coalesce(string_agg(g.name, ',' ORDER BY g.name), '')
                FROM transactions t
                LEFT JOIN transaction_tags tt ON tt.transaction_id = t.id
                LEFT JOIN tags g ON g.id = tt.tag_id
                WHERE t.name = 'Netflix' AND t.deleted_at IS NULL
                GROUP BY t.id, t.recurrence_date
                ORDER BY t.recurrence_date
                """, String.class);
    }

    private List<Map<String, Object>> transactionsOf(UUID accountId) {
        syncDatabase();
        return jdbc.queryForList("""
                SELECT * FROM transactions WHERE account_id = ? AND deleted_at IS NULL ORDER BY datetime
                """, accountId);
    }

    /** Requests share the test's persistence context: write pending changes before reading with JDBC. */
    private void syncDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private UUID insertAccount(TestUser owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, owner.id(), name);
    }
}
