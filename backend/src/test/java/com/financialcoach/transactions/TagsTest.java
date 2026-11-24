package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class TagsTest extends ApiIntegrationTest {

    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String CSV_HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes";

    @Autowired
    private RateLimiter rateLimiter;

    private TestUser alice;
    private TestUser bob;
    private UUID aliceCard;
    private UUID aliceCash;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        aliceCard = account(alice, "Debit card");
        aliceCash = account(alice, "Cash");
    }

    @Nested
    class OnTransactions {

        @Test
        void createdTransactionsReturnTheirTagsCleanedAndSorted() throws Exception {
            create(alice, aliceCard, "Tacos", "120.00", "[\" #Trip  Cancun \", \"work\", \"WORK\"]")
                    .andExpect(status().isCreated())
                    // "#" removed, spaces collapsed, "WORK" is the same tag as "work".
                    .andExpect(jsonPath("$.tags", contains("Trip Cancun", "work")));
        }

        @Test
        void aNameUsedBeforeReusesTheTagWithItsFirstSpelling() throws Exception {
            create(alice, aliceCard, "Tacos", "120.00", "[\"Trip Cancun\"]");
            create(alice, aliceCard, "Hotel", "900.00", "[\"trip cancun\"]")
                    .andExpect(jsonPath("$.tags", contains("Trip Cancun")));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE user_id = ?", Integer.class, alice.id()))
                    .isEqualTo(1);
        }

        @Test
        void eachUserHasTheirOwnTags() throws Exception {
            create(alice, aliceCard, "Tacos", "120.00", "[\"work\"]");
            create(bob, account(bob, "Card"), "Taxi", "80.00", "[\"work\"]");

            assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE lower(name) = 'work'", Integer.class))
                    .isEqualTo(2);
        }

        @Test
        void patchReplacesTheTagsAndAnEmptyListRemovesThem() throws Exception {
            String id = idOf(create(alice, aliceCard, "Tacos", "120.00", "[\"work\", \"food\"]"));

            patchJson(id, "{\"tags\": [\"gift\"]}").andExpect(jsonPath("$.tags", contains("gift")));
            patchJson(id, "{\"tags\": []}").andExpect(jsonPath("$.tags", empty()));
            // Without "tags", the tags stay as they are.
            patchJson(id, "{\"tags\": [\"gift\"]}");
            patchJson(id, "{\"name\": \"Tacos al pastor\"}").andExpect(jsonPath("$.tags", contains("gift")));
        }

        @Test
        void rejectsInvalidTags() throws Exception {
            String tooLong = "x".repeat(41);
            create(alice, aliceCard, "Tacos", "120.00", "[\"  \", \"" + tooLong + "\", \"a;b\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].message", contains(
                            "must not contain empty tags",
                            "each tag must have at most 40 characters",
                            "tags must not contain \";\"")));

            String eleven = "[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 11)
                    .mapToObj(i -> "\"t" + i + "\"").toList()) + "]";
            create(alice, aliceCard, "Tacos", "120.00", eleven)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("at most 10 tags per transaction"));
        }
    }

    @Nested
    class Filtering {

        @BeforeEach
        void createTagged() throws Exception {
            create(alice, aliceCard, "Tacos", "120.00", "[\"Trip Cancun\", \"food\"]");
            create(alice, aliceCard, "Hotel", "900.00", "[\"Trip Cancun\"]");
            create(alice, aliceCard, "Oxxo", "45.00", "[]");
        }

        @Test
        void filtersByOneTagIgnoringCase() throws Exception {
            list("tag", "trip cancun").andExpect(jsonPath("$.content[*].name", contains("Hotel", "Tacos")));
            list("tag", "food").andExpect(jsonPath("$.content[*].name", contains("Tacos")));
            list("tag", "nothing").andExpect(jsonPath("$.total_elements").value(0));
        }

        @Test
        void theSearchBoxAlsoFindsTags() throws Exception {
            list("q", "cancun").andExpect(jsonPath("$.content[*].name", contains("Hotel", "Tacos")));
        }

        @Test
        void anotherUsersTagWithTheSameNameDoesNotMatch() throws Exception {
            create(bob, account(bob, "Card"), "Flight", "3000.00", "[\"Trip Cancun\"]");

            list("tag", "Trip Cancun").andExpect(jsonPath("$.total_elements").value(2));
        }

        @Test
        void tagsListsTheTagsInUseMostUsedFirst() throws Exception {
            create(alice, aliceCash, "Snack", "20.00", "[\"food\"]");

            mvc.perform(get("/api/v1/tags").with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", contains("food", "Trip Cancun")))
                    .andExpect(jsonPath("$[*].transactions", contains(2, 2)));
            // Only one account.
            mvc.perform(get("/api/v1/tags").param("account_id", aliceCash.toString()).with(as(alice)))
                    .andExpect(jsonPath("$[*].name", contains("food")))
                    .andExpect(jsonPath("$[0].transactions").value(1));
        }

        @Test
        void deletedTransactionsDoNotCountAndAnUnusedTagDisappears() throws Exception {
            String tacos = JsonPath.read(list("tag", "food").andReturn().getResponse().getContentAsString(), "$.content[0].id");
            mvc.perform(delete(TRANSACTIONS + "/" + tacos).with(as(alice))).andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/tags").with(as(alice)))
                    .andExpect(jsonPath("$[*].name", contains("Trip Cancun")))
                    .andExpect(jsonPath("$[0].transactions").value(1));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            mvc.perform(get("/api/v1/tags").param("account_id", aliceCard.toString()).with(as(bob)))
                    .andExpect(status().isNotFound());
        }

        private ResultActions list(String param, String value) throws Exception {
            return mvc.perform(get(TRANSACTIONS).param("account_id", aliceCard.toString()).param(param, value)
                    .with(as(alice)));
        }
    }

    @Nested
    class Csv {

        @Test
        void importsTheOptionalTagsColumn() throws Exception {
            upload(alice, CSV_HEADER + ",Tags\n"
                    + "Expense,2026-09-25 15:58:12,15:58,Tacos,120,Food,,Debit card,,\"Trip Cancun; food\"\n"
                    + "Expense,2026-09-26 10:00:00,10:00,Oxxo,45,Food,,Debit card,,\n")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(2));

            mvc.perform(get(TRANSACTIONS + "?account_id=" + aliceCard + "&tag=food").with(as(alice)))
                    .andExpect(jsonPath("$.content[*].name", contains("Tacos")))
                    .andExpect(jsonPath("$.content[0].tags", contains("food", "Trip Cancun")));
        }

        @Test
        void filesWithoutTheTagsColumnStillWork() throws Exception {
            upload(alice, CSV_HEADER + "\nExpense,2026-09-25 15:58:12,15:58,Tacos,120,Food,,Debit card,\n")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(1));
        }

        @Test
        void anInvalidTagIsReportedWithItsRow() throws Exception {
            upload(alice, CSV_HEADER + ",Tags\n"
                    + "Expense,2026-09-25 15:58:12,15:58,Tacos,120,Food,,Debit card,," + "x".repeat(41) + "\n")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CSV_INVALID_ROWS"))
                    .andExpect(jsonPath("$.errors[0].row").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("Tags"));
        }

        @Test
        void theExportWritesTheTagsAndImportingItBringsThemBack() throws Exception {
            create(alice, aliceCard, "Tacos", "120.00", "[\"Trip Cancun\", \"food\"]");

            String exported = mvc.perform(get(TRANSACTIONS + "/export").with(as(alice)))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(exported).contains(",Tags\r\n").contains(",food; Trip Cancun\r\n");

            upload(bob, exported).andExpect(jsonPath("$.records_imported").value(1));
            String bobAccount = jdbc.queryForObject("SELECT id::text FROM accounts WHERE user_id = ?", String.class, bob.id());
            mvc.perform(get(TRANSACTIONS + "?account_id=" + bobAccount).with(as(bob)))
                    .andExpect(jsonPath("$.content[0].tags", contains("food", "Trip Cancun")));
        }
    }

    private ResultActions create(TestUser user, UUID accountId, String name, String amount, String tagsJson)
            throws Exception {
        String json = """
                {"type": "EXPENSE", "datetime": "2026-03-01T09:00", "name": "%s", "amount": "%s",
                 "category_group": "Food", "account_id": "%s", "tags": %s}
                """.formatted(name, amount, accountId, tagsJson);
        return mvc.perform(post(TRANSACTIONS).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchJson(String id, String json) throws Exception {
        return mvc.perform(patch(TRANSACTIONS + "/" + id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions upload(TestUser user, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart(TRANSACTIONS + "/upload-csv").file(file).with(as(user)));
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID account(TestUser owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, owner.id(), name);
    }
}
