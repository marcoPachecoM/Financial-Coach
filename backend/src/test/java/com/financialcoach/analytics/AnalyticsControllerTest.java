package com.financialcoach.analytics;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class AnalyticsControllerTest extends ApiIntegrationTest {

    private static final String SUMMARY = "/api/v1/analytics/summary";
    private static final String BY_CATEGORY_GROUP = "/api/v1/analytics/by-category-group";
    private static final String DAILY = "/api/v1/analytics/daily";

    private AnalyticsTestData data;
    private TestUser alice;
    private TestUser bob;
    private UUID card;
    private UUID cash;
    private UUID bobCard;

    @BeforeEach
    void createData() {
        data = new AnalyticsTestData(jdbc);
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        card = data.account(alice, "Debit card", null);
        cash = data.account(alice, "Cash", null);
        bobCard = data.account(bob, "Debit card", null);

        data.expense(alice, card, "2026-01-02T13:35:00", "100.00", "Food");
        data.expense(alice, card, "2026-01-10T20:00:00", "250.50", "Entertainment");
        data.expense(alice, card, "2026-01-31T23:59:59", "49.50", "Food");
        data.income(alice, card, "2026-01-15T09:00:00", "20000.00", "Salary");
        data.income(alice, card, "2026-02-01T00:00:00", "500.00", "Gift");
        // Must never be counted:
        data.deletedExpense(alice, card, "2026-01-05T10:00:00", "999.00", "Food");
        data.expense(alice, cash, "2026-01-05T10:00:00", "777.00", "Food");
        data.expense(bob, bobCard, "2026-01-05T10:00:00", "555.00", "Food");
    }

    @Nested
    class Summary {

        @Test
        void aggregatesTheWholeHistory() throws Exception {
            summary(alice, "account_id=" + card)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_expenses").value("400.00"))
                    .andExpect(jsonPath("$.total_income").value("20500.00"))
                    .andExpect(jsonPath("$.net").value("20100.00"))
                    .andExpect(jsonPath("$.expense_count").value(3))
                    .andExpect(jsonPath("$.income_count").value(2))
                    .andExpect(jsonPath("$.average_expense").value("133.33"))
                    .andExpect(jsonPath("$.largest_expense").value("250.50"))
                    .andExpect(jsonPath("$.first_date").value("2026-01-02"))
                    .andExpect(jsonPath("$.last_date").value("2026-02-01"));
        }

        @Test
        void bothDatesAreInclusive() throws Exception {
            summary(alice, "account_id=%s&start_date=2026-01-10&end_date=2026-01-31".formatted(card))
                    .andExpect(jsonPath("$.total_expenses").value("300.00"))
                    .andExpect(jsonPath("$.expense_count").value(2))
                    .andExpect(jsonPath("$.total_income").value("20000.00"))
                    .andExpect(jsonPath("$.income_count").value(1))
                    .andExpect(jsonPath("$.first_date").value("2026-01-10"))
                    .andExpect(jsonPath("$.last_date").value("2026-01-31"));
        }

        @Test
        void netIsNegativeWhenSpendingMoreThanEarning() throws Exception {
            summary(alice, "account_id=" + cash)
                    .andExpect(jsonPath("$.total_income").value("0.00"))
                    .andExpect(jsonPath("$.net").value("-777.00"));
        }

        @Test
        void emptyPeriodIsAllZeros() throws Exception {
            summary(alice, "account_id=%s&start_date=2030-01-01".formatted(card))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_expenses").value("0.00"))
                    .andExpect(jsonPath("$.total_income").value("0.00"))
                    .andExpect(jsonPath("$.net").value("0.00"))
                    .andExpect(jsonPath("$.expense_count").value(0))
                    .andExpect(jsonPath("$.income_count").value(0))
                    .andExpect(jsonPath("$.average_expense").value("0.00"))
                    .andExpect(jsonPath("$.largest_expense").value("0.00"))
                    .andExpect(jsonPath("$.first_date").value(nullValue()))
                    .andExpect(jsonPath("$.last_date").value(nullValue()));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            summary(alice, "account_id=" + bobCard)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        void endDateBeforeStartDate() throws Exception {
            summary(alice, "account_id=%s&start_date=2026-02-01&end_date=2026-01-01".formatted(card))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("end_date"));
        }

        @Test
        void accountIdIsRequired() throws Exception {
            summary(alice, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("account_id"));
        }

        @Test
        void unknownParametersAreRejected() throws Exception {
            summary(alice, "account_id=%s&type=EXPENSE".formatted(card))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("type"));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get(SUMMARY + "?account_id=" + card)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ByCategoryGroup {

        @Test
        void expensesByDefaultLargestFirst() throws Exception {
            byCategoryGroup(alice, "account_id=" + card)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].category_group").value("Entertainment"))
                    .andExpect(jsonPath("$[0].amount").value("250.50"))
                    .andExpect(jsonPath("$[0].percentage").value("62.63"))
                    .andExpect(jsonPath("$[0].count").value(1))
                    .andExpect(jsonPath("$[1].category_group").value("Food"))
                    .andExpect(jsonPath("$[1].amount").value("149.50"))
                    .andExpect(jsonPath("$[1].percentage").value("37.38"))
                    .andExpect(jsonPath("$[1].count").value(2));
        }

        @Test
        void incomeAmountsArePositive() throws Exception {
            byCategoryGroup(alice, "account_id=%s&type=INCOME".formatted(card))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].category_group").value("Salary"))
                    .andExpect(jsonPath("$[0].amount").value("20000.00"))
                    .andExpect(jsonPath("$[0].percentage").value("97.56"))
                    .andExpect(jsonPath("$[1].category_group").value("Gift"))
                    .andExpect(jsonPath("$[1].amount").value("500.00"));
        }

        @Test
        void filtersByDate() throws Exception {
            byCategoryGroup(alice, "account_id=%s&start_date=2026-01-31&end_date=2026-01-31".formatted(card))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].category_group").value("Food"))
                    .andExpect(jsonPath("$[0].amount").value("49.50"))
                    .andExpect(jsonPath("$[0].percentage").value("100.00"));
        }

        @Test
        void emptyPeriodIsAnEmptyList() throws Exception {
            byCategoryGroup(alice, "account_id=%s&start_date=2030-01-01".formatted(card))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        void invalidType() throws Exception {
            byCategoryGroup(alice, "account_id=%s&type=TRANSFER".formatted(card))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("type"));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            byCategoryGroup(alice, "account_id=" + bobCard).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Daily {

        @Test
        void oneEntryPerDayWithTransactionsOldestFirst() throws Exception {
            data.expense(alice, card, "2026-01-02T20:00:00", "30.00", "Food");

            daily(alice, "account_id=" + card)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].date", contains(
                            "2026-01-02", "2026-01-10", "2026-01-15", "2026-01-31", "2026-02-01")))
                    // Two expenses on the 2nd; a day with only income spends nothing but still counts.
                    .andExpect(jsonPath("$[0].expense_total").value("130.00"))
                    .andExpect(jsonPath("$[0].expense_count").value(2))
                    .andExpect(jsonPath("$[0].transaction_count").value(2))
                    .andExpect(jsonPath("$[2].expense_total").value("0.00"))
                    .andExpect(jsonPath("$[2].expense_count").value(0))
                    .andExpect(jsonPath("$[2].transaction_count").value(1));
        }

        @Test
        void filtersByDateAndCategoryGroup() throws Exception {
            daily(alice, "account_id=%s&start_date=2026-01-10&end_date=2026-01-31".formatted(card))
                    .andExpect(jsonPath("$[*].date", contains("2026-01-10", "2026-01-15", "2026-01-31")));
            daily(alice, "account_id=%s&category_group=Food".formatted(card))
                    .andExpect(jsonPath("$[*].date", contains("2026-01-02", "2026-01-31")))
                    .andExpect(jsonPath("$[1].expense_total").value("49.50"));
        }

        @Test
        void emptyPeriodIsAnEmptyList() throws Exception {
            daily(alice, "account_id=%s&start_date=2030-01-01".formatted(card))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        void invalidCategoryGroup() throws Exception {
            daily(alice, "account_id=%s&category_group=Rockets".formatted(card))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("category_group"));
        }

        @Test
        void anotherUsersAccountIsNotFound() throws Exception {
            daily(alice, "account_id=" + bobCard).andExpect(status().isNotFound());
        }

        @Test
        void unknownParametersAreRejected() throws Exception {
            daily(alice, "account_id=%s&type=EXPENSE".formatted(card)).andExpect(status().isBadRequest());
        }
    }

    private ResultActions daily(TestUser user, String query) throws Exception {
        return mvc.perform(get(DAILY + "?" + query).with(as(user)));
    }

    private ResultActions summary(TestUser user, String query) throws Exception {
        return mvc.perform(get(SUMMARY + "?" + query).with(as(user)));
    }

    private ResultActions byCategoryGroup(TestUser user, String query) throws Exception {
        return mvc.perform(get(BY_CATEGORY_GROUP + "?" + query).with(as(user)));
    }
}
