package com.financialcoach.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AccountControllerTest extends ApiIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String UTC_INSTANT = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsers() {
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
    }

    @Nested
    class Create {

        @Test
        void createsAccountWithBudget() throws Exception {
            postAccount(alice, """
                    {"name": "  Debit card  ", "monthly_budget": "5000"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern(".*/api/v1/accounts/[0-9a-f-]{36}$")))
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.name").value("Debit card"))
                    .andExpect(jsonPath("$.monthly_budget").value("5000.00"))
                    .andExpect(jsonPath("$.created_at").value(matchesPattern(UTC_INSTANT)))
                    .andExpect(jsonPath("$.updated_at").value(matchesPattern(UTC_INSTANT)));
        }

        @Test
        void storesTheAuthenticatedUserAsOwner() throws Exception {
            String id = createAccount(alice, "Debit card");

            UUID owner = jdbc.queryForObject("SELECT user_id FROM accounts WHERE id = ?::uuid", UUID.class, id);
            assertThat(owner).isEqualTo(alice.id());
        }

        @Test
        void budgetIsOptionalAndReturnedAsNull() throws Exception {
            postAccount(alice, """
                    {"name": "Cash"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.monthly_budget").value(nullValue()));
        }

        @Test
        void rejectsDuplicateNameIgnoringCase() throws Exception {
            createAccount(alice, "Debit card");

            postAccount(alice, """
                    {"name": "debit CARD"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        void sameNameIsAllowedForDifferentUsers() throws Exception {
            createAccount(alice, "Debit card");

            postAccount(bob, """
                    {"name": "Debit card"}
                    """)
                    .andExpect(status().isCreated());
        }

        @Test
        void reportsEveryInvalidField() throws Exception {
            postAccount(alice, """
                    {"name": "   ", "monthly_budget": "12.345"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    .andExpect(jsonPath("$.errors[0].field").value("monthly_budget"))
                    .andExpect(jsonPath("$.errors[1].field").value("name"));
        }

        @Test
        void rejectsBudgetThatDoesNotFitTheColumn() throws Exception {
            // numeric(14,2) holds 12 integer digits: 13 must be a 400, not a database error (500).
            postAccount(alice, """
                    {"name": "Cash", "monthly_budget": "1234567890123"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("monthly_budget"));
        }

        @Test
        void rejectsNegativeBudget() throws Exception {
            postAccount(alice, """
                    {"name": "Cash", "monthly_budget": "-1"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("monthly_budget"));
        }

        @Test
        void rejectsUnknownFields() throws Exception {
            postAccount(alice, """
                    {"name": "Cash", "user_id": "%s"}
                    """.formatted(bob.id()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("user_id"))
                    .andExpect(jsonPath("$.errors[0].message").value("Unknown field."));
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            postAccount(alice, "{\"name\": ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Cash\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Read {

        @Test
        void listsOnlyOwnAccountsSortedByNameIgnoringCase() throws Exception {
            createAccount(alice, "Savings");
            createAccount(alice, "cash");
            createAccount(alice, "Debit card");
            createAccount(bob, "Bob's account");

            mvc.perform(get(ACCOUNTS).with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", contains("cash", "Debit card", "Savings")));
        }

        @Test
        void listIsEmptyForNewUser() throws Exception {
            mvc.perform(get(ACCOUNTS).with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void getsOwnAccount() throws Exception {
            String id = createAccount(alice, "Debit card");

            mvc.perform(get(ACCOUNTS + "/" + id).with(as(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("Debit card"));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            String id = createAccount(alice, "Debit card");

            mvc.perform(get(ACCOUNTS + "/" + id).with(as(bob)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.instance").value(endsWith(id)))
                    // Same shape as security errors: "type" omitted (about:blank).
                    .andExpect(jsonPath("$.type").doesNotExist());
        }

        @Test
        void unknownIdIsNotFound() throws Exception {
            mvc.perform(get(ACCOUNTS + "/" + UUID.randomUUID()).with(as(alice)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void malformedIdIsBadRequest() throws Exception {
            mvc.perform(get(ACCOUNTS + "/not-a-uuid").with(as(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    class Update {

        private String accountId;

        @BeforeEach
        void createAccountWithBudget() throws Exception {
            accountId = JsonPath.read(postAccount(alice, """
                    {"name": "Debit card", "monthly_budget": "5000.00"}
                    """).andReturn().getResponse().getContentAsString(), "$.id");
        }

        @Test
        void renamesAndKeepsFieldsThatWereNotSent() throws Exception {
            patchAccount(alice, accountId, """
                    {"name": "Main card"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Main card"))
                    .andExpect(jsonPath("$.monthly_budget").value("5000.00"));
        }

        @Test
        void nullBudgetRemovesTheBudget() throws Exception {
            patchAccount(alice, accountId, """
                    {"monthly_budget": null}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Debit card"))
                    .andExpect(jsonPath("$.monthly_budget").value(nullValue()));
        }

        @Test
        void changesBudget() throws Exception {
            patchAccount(alice, accountId, """
                    {"monthly_budget": "7500.5"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monthly_budget").value("7500.50"));
        }

        @Test
        void canChangeTheCaseOfItsOwnName() throws Exception {
            patchAccount(alice, accountId, """
                    {"name": "DEBIT CARD"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("DEBIT CARD"));
        }

        @Test
        void rejectsRenamingToAnotherAccountsName() throws Exception {
            createAccount(alice, "Cash");

            patchAccount(alice, accountId, """
                    {"name": "cash"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        void rejectsEmptyBody() throws Exception {
            patchAccount(alice, accountId, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void rejectsNullName() throws Exception {
            patchAccount(alice, accountId, """
                    {"name": null}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        void anotherUserCannotEditTheAccount() throws Exception {
            patchAccount(bob, accountId, """
                    {"name": "Hacked"}
                    """)
                    .andExpect(status().isNotFound());

            String name = jdbc.queryForObject("SELECT name FROM accounts WHERE id = ?::uuid", String.class, accountId);
            assertThat(name).isEqualTo("Debit card");
        }
    }

    private ResultActions postAccount(TestUser user, String json) throws Exception {
        return mvc.perform(post(ACCOUNTS).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchAccount(TestUser user, String id, String json) throws Exception {
        return mvc.perform(patch(ACCOUNTS + "/" + id).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAccount(TestUser user, String name) throws Exception {
        String body = postAccount(user, "{\"name\": \"%s\"}".formatted(name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
