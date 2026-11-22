package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TransactionControllerTest extends ApiIntegrationTest {

    private static final String TRANSACTIONS = "/api/v1/transactions";

    private TestUser alice;
    private TestUser bob;
    private UUID aliceCard;
    private UUID aliceCash;
    private UUID bobCard;

    @BeforeEach
    void createUsersAndAccounts() {
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        aliceCard = insertAccount(alice, "Debit card");
        aliceCash = insertAccount(alice, "Cash");
        bobCard = insertAccount(bob, "Debit card");
    }

    @Nested
    class Create {

        @Test
        void createsExpense() throws Exception {
            postTransaction(alice, """
                    {
                      "type": "EXPENSE",
                      "datetime": "2026-01-02T13:35",
                      "name": "  La tiendita  ",
                      "amount": "100",
                      "category_group": "Utilities",
                      "category": "  Gas  ",
                      "account_id": "%s"
                    }
                    """.formatted(aliceCard))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern(".*/api/v1/transactions/[0-9a-f-]{36}$")))
                    .andExpect(jsonPath("$.user_id").value(alice.id().toString()))
                    .andExpect(jsonPath("$.account_id").value(aliceCard.toString()))
                    .andExpect(jsonPath("$.type").value("EXPENSE"))
                    .andExpect(jsonPath("$.datetime").value("2026-01-02T13:35:00"))
                    .andExpect(jsonPath("$.name").value("La tiendita"))
                    .andExpect(jsonPath("$.amount").value("100.00"))
                    .andExpect(jsonPath("$.category_group").value("Utilities"))
                    .andExpect(jsonPath("$.category").value("Gas"))
                    .andExpect(jsonPath("$.notes").value(nullValue()))
                    .andExpect(jsonPath("$.created_at").exists());
        }

        @Test
        void recordsChannelAndWhoChoseTheCategory() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "100", "Food");

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT source, category_group_source, fingerprint FROM transactions WHERE id = ?::uuid", id);
            assertThat(row).containsEntry("source", "MANUAL").containsEntry("category_group_source", "USER");
            assertThat(row.get("fingerprint")).isNull();
        }

        @Test
        void acceptsIncomeWithNegativeAmountAndGroupWithSpaces() throws Exception {
            postTransaction(alice, body("INCOME", "-20000.50", "Higiene personal", aliceCard))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amount").value("-20000.50"))
                    .andExpect(jsonPath("$.category_group").value("Higiene personal"))
                    .andExpect(jsonPath("$.category").value(nullValue()));
        }

        @Test
        void rejectsIncomeWithPositiveAmount() throws Exception {
            postTransaction(alice, body("INCOME", "500", "Salary", aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors[0].field").value("amount"))
                    .andExpect(jsonPath("$.errors[0].message").value("INCOME amounts must be negative."));
        }

        @Test
        void rejectsExpenseWithNegativeAmount() throws Exception {
            postTransaction(alice, body("EXPENSE", "-5", "Food", aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("EXPENSE amounts must be positive."));
        }

        @Test
        void rejectsZeroAmount() throws Exception {
            postTransaction(alice, body("EXPENSE", "0.00", "Food", aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
        }

        @Test
        void categorizesAutomaticallyFromTheMerchantCache() throws Exception {
            jdbc.update("INSERT INTO merchant_category_cache (merchant_name, category_group, source) VALUES ('la tiendita', 'Snacks', 'LLM')");

            String id = createTransaction(alice, aliceCard, "EXPENSE", "35", null);

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(jsonPath("$.category_group").value("Snacks"));
            assertThat(jdbc.queryForObject("SELECT category_group_source FROM transactions WHERE id = ?::uuid", String.class, id))
                    .isEqualTo("LLM");
        }

        @Test
        void unknownMerchantGetsTheDefaultGroup() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "35", null);

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(jsonPath("$.category_group").value("Other"));
        }

        @Test
        void rejectsUnknownCategoryGroup() throws Exception {
            postTransaction(alice, body("EXPENSE", "10", "Groceries", aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("category_group"));
        }

        @Test
        void categoryIsFreeTextUpTo100Characters() throws Exception {
            postTransaction(alice, """
                    {"type": "EXPENSE", "datetime": "2026-01-02T13:35", "name": "Shop", "amount": "10",
                     "category": "%s", "account_id": "%s"}
                    """.formatted("x".repeat(101), aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("category"));
        }

        @Test
        void rejectsUnsupportedType() throws Exception {
            postTransaction(alice, body("TRANSFER", "10", "Food", aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("type"));
        }

        @Test
        void reportsAllMissingRequiredFields() throws Exception {
            postTransaction(alice, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", contains("account_id", "amount", "datetime", "name", "type")));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            postTransaction(alice, body("EXPENSE", "10", "Food", bobCard))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Account not found."));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        }
    }

    @Nested
    class ListTransactions {

        @BeforeEach
        void createThree() throws Exception {
            postTransaction(alice, at("2026-01-01T09:00")).andExpect(status().isCreated());
            postTransaction(alice, at("2026-01-15T12:00")).andExpect(status().isCreated());
            postTransaction(alice, at("2026-01-31T23:59")).andExpect(status().isCreated());
        }

        @Test
        void returnsPagesNewestFirst() throws Exception {
            list(alice, "account_id=%s&size=2".formatted(aliceCard))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].datetime", contains("2026-01-31T23:59:00", "2026-01-15T12:00:00")))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.total_elements").value(3))
                    .andExpect(jsonPath("$.total_pages").value(2))
                    .andExpect(jsonPath("$.has_more").value(true));

            list(alice, "account_id=%s&size=2&page=2".formatted(aliceCard))
                    .andExpect(jsonPath("$.content[*].datetime", contains("2026-01-01T09:00:00")))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.has_more").value(false));
        }

        @Test
        void dateFilterIncludesBothEndDays() throws Exception {
            list(alice, "account_id=%s&start_date=2026-01-15&end_date=2026-01-31".formatted(aliceCard))
                    .andExpect(jsonPath("$.content[*].datetime", contains("2026-01-31T23:59:00", "2026-01-15T12:00:00")));
        }

        @Test
        void onlyIncludesTheRequestedAccount() throws Exception {
            postTransaction(alice, body("EXPENSE", "10", "Food", aliceCash)).andExpect(status().isCreated());

            list(alice, "account_id=" + aliceCash)
                    .andExpect(jsonPath("$.total_elements").value(1));
        }

        @Test
        void excludesDeletedTransactions() throws Exception {
            String id = JsonPath.read(list(alice, "account_id=" + aliceCard)
                    .andReturn().getResponse().getContentAsString(), "$.content[0].id");
            mvc.perform(delete(TRANSACTIONS + "/" + id).with(as(alice))).andExpect(status().isNoContent());

            list(alice, "account_id=" + aliceCard)
                    .andExpect(jsonPath("$.total_elements").value(2));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            list(bob, "account_id=" + aliceCard)
                    .andExpect(status().isNotFound());
        }

        @Test
        void requiresAccountId() throws Exception {
            list(alice, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("account_id"));
        }

        @Test
        void rejectsPageSizeAbove100() throws Exception {
            list(alice, "account_id=%s&size=101".formatted(aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("size"));
        }

        @Test
        void rejectsStartDateAfterEndDate() throws Exception {
            list(alice, "account_id=%s&start_date=2026-02-01&end_date=2026-01-01".formatted(aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("end_date"));
        }

        @Test
        void rejectsMalformedDate() throws Exception {
            list(alice, "account_id=%s&start_date=01/02/2026".formatted(aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("start_date"));
        }

        @Test
        void rejectsUnknownQueryParameters() throws Exception {
            list(alice, "account_id=%s&sort=amount".formatted(aliceCard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("sort"));
        }

        private String at(String datetime) {
            return """
                    {"type": "EXPENSE", "datetime": "%s", "name": "Shop", "amount": "10",
                     "category_group": "Shopping", "account_id": "%s"}
                    """.formatted(datetime, aliceCard);
        }

        private ResultActions list(TestUser user, String query) throws Exception {
            return mvc.perform(get(TRANSACTIONS + "?" + query).with(as(user)));
        }
    }

    @Nested
    class GetOne {

        @Test
        void getsOwnTransaction() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "10", "Food");

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        void anotherUsersTransactionIsNotFound() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "10", "Food");

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(bob)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    class Update {

        private String id;

        @BeforeEach
        void createExpense() throws Exception {
            id = createTransaction(alice, aliceCard, "EXPENSE", "100", null);
        }

        @Test
        void userChosenGroupIsRecorded() throws Exception {
            patchTransaction(alice, id, """
                    {"category_group": "Health"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category_group").value("Health"))
                    .andExpect(jsonPath("$.amount").value("100.00"));

            assertThat(jdbc.queryForObject("SELECT category_group_source FROM transactions WHERE id = ?::uuid", String.class, id))
                    .isEqualTo("USER");
        }

        @Test
        void changingOnlyTheTypeBreaksTheSignRule() throws Exception {
            patchTransaction(alice, id, """
                    {"type": "INCOME"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("INCOME amounts must be negative."));
        }

        @Test
        void changesTypeAndAmountTogether() throws Exception {
            patchTransaction(alice, id, """
                    {"type": "INCOME", "amount": "-100"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("INCOME"))
                    .andExpect(jsonPath("$.amount").value("-100.00"));
        }

        @Test
        void setsAndClearsOptionalFields() throws Exception {
            patchTransaction(alice, id, """
                    {"notes": "Birthday", "category": "Flowers"}
                    """)
                    .andExpect(jsonPath("$.notes").value("Birthday"))
                    .andExpect(jsonPath("$.category").value("Flowers"));

            patchTransaction(alice, id, """
                    {"notes": null}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notes").value(nullValue()))
                    .andExpect(jsonPath("$.category").value("Flowers"));
        }

        @Test
        void rejectsNullForRequiredFields() throws Exception {
            patchTransaction(alice, id, """
                    {"name": null}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        void movesToAnotherOwnAccount() throws Exception {
            patchTransaction(alice, id, """
                    {"account_id": "%s"}
                    """.formatted(aliceCash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account_id").value(aliceCash.toString()));
        }

        @Test
        void cannotMoveToAnotherUsersAccount() throws Exception {
            patchTransaction(alice, id, """
                    {"account_id": "%s"}
                    """.formatted(bobCard))
                    .andExpect(status().isNotFound());

            assertThat(jdbc.queryForObject("SELECT account_id FROM transactions WHERE id = ?::uuid", UUID.class, id))
                    .isEqualTo(aliceCard);
        }

        @Test
        void rejectsEmptyBody() throws Exception {
            patchTransaction(alice, id, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void anotherUserCannotEdit() throws Exception {
            patchTransaction(bob, id, """
                    {"amount": "1"}
                    """)
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Delete {

        @Test
        void softDeletesTheTransaction() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "10", "Food");

            mvc.perform(delete(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(status().isNotFound());
            // The row is still there, only marked as deleted.
            assertThat(jdbc.queryForObject(
                    "SELECT deleted_at IS NOT NULL FROM transactions WHERE id = ?::uuid", Boolean.class, id))
                    .isTrue();
        }

        @Test
        void deletingTwiceIsNotFound() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "10", "Food");
            mvc.perform(delete(TRANSACTIONS + "/" + id).with(as(alice))).andExpect(status().isNoContent());

            mvc.perform(delete(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anotherUserCannotDelete() throws Exception {
            String id = createTransaction(alice, aliceCard, "EXPENSE", "10", "Food");

            mvc.perform(delete(TRANSACTIONS + "/" + id).with(as(bob)))
                    .andExpect(status().isNotFound());
            mvc.perform(get(TRANSACTIONS + "/" + id).with(as(alice)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(TRANSACTIONS + "?account_id=" + aliceCard))
                .andExpect(status().isUnauthorized());
    }

    private UUID insertAccount(TestUser owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, owner.id(), name);
    }

    private static String body(String type, String amount, String categoryGroup, UUID accountId) {
        String groupField = categoryGroup == null ? "" : "\"category_group\": \"%s\",".formatted(categoryGroup);
        return """
                {"type": "%s", "datetime": "2026-01-02T13:35", "name": "La tiendita",
                 "amount": "%s", %s "account_id": "%s"}
                """.formatted(type, amount, groupField, accountId);
    }

    private ResultActions postTransaction(TestUser user, String json) throws Exception {
        return mvc.perform(post(TRANSACTIONS).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchTransaction(TestUser user, String id, String json) throws Exception {
        return mvc.perform(patch(TRANSACTIONS + "/" + id).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createTransaction(TestUser user, UUID accountId, String type, String amount, String categoryGroup)
            throws Exception {
        String response = postTransaction(user, body(type, amount, categoryGroup, accountId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
