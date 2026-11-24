package com.financialcoach.analytics;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET} and {@code PUT /accounts/{id}/category-budgets}. "Today" is 2026-11-21. */
@Import(FixedClockConfiguration.class)
class CategoryBudgetsTest extends ApiIntegrationTest {

    @Autowired
    private MutableClock clock;

    private AnalyticsTestData data;
    private TestUser alice;
    private UUID card;

    @BeforeEach
    void createData() {
        clock.setToday(FixedClockConfiguration.TODAY);
        data = new AnalyticsTestData(jdbc);
        alice = users.create("alice", UserRole.CLIENT, true);
        card = data.account(alice, "Debit card", "5000.00");

        data.expense(alice, card, "2026-11-02T10:00:00", "850.00", "Food");
        data.expense(alice, card, "2026-11-10T21:00:00", "360.00", "Entertainment");
        data.expense(alice, card, "2026-11-12T12:00:00", "200.00", "Shopping");
        data.expense(alice, card, "2026-11-13T12:00:00", "50.00", "Others");
        data.income(alice, card, "2026-11-15T09:00:00", "20000.00", "Salary");      // income does not count
        data.deletedExpense(alice, card, "2026-11-05T10:00:00", "999.00", "Food");  // deleted does not count
        data.expense(alice, card, "2026-10-20T10:00:00", "100.00", "Food");         // another month
    }

    @Test
    void savesTheBudgetsAndReportsTheirUsageMostUsedFirst() throws Exception {
        replace(alice, """
                {"budgets": [{"category_group": "Food", "monthly_amount": "1000"},
                             {"category_group": "Entertainment", "monthly_amount": "300.00"},
                             {"category_group": "Travel", "monthly_amount": "500"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(card.toString()))
                .andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.budgets[*].category_group", contains("Entertainment", "Food", "Travel")))
                .andExpect(jsonPath("$.budgets[0].budget").value("300.00"))
                .andExpect(jsonPath("$.budgets[0].spent").value("360.00"))
                .andExpect(jsonPath("$.budgets[0].remaining").value("-60.00"))
                .andExpect(jsonPath("$.budgets[0].percentage_used").value("120.00"))
                .andExpect(jsonPath("$.budgets[0].status").value("EXCEEDED"))
                .andExpect(jsonPath("$.budgets[1].spent").value("850.00"))
                .andExpect(jsonPath("$.budgets[1].status").value("WARNING"))
                .andExpect(jsonPath("$.budgets[2].spent").value("0.00"))
                .andExpect(jsonPath("$.budgets[2].status").value("ON_TRACK"))
                // Spending of the groups without a budget, largest first.
                .andExpect(jsonPath("$.unbudgeted[*].category_group", contains("Shopping", "Others")))
                .andExpect(jsonPath("$.unbudgeted[0].spent").value("200.00"))
                .andExpect(jsonPath("$.unbudgeted[0].count").value(1));

        categoryBudgets(alice, "?month=2026-10")
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.budgets[*].category_group", contains("Food", "Entertainment", "Travel")))
                .andExpect(jsonPath("$.budgets[0].spent").value("100.00"))
                .andExpect(jsonPath("$.unbudgeted", empty()));
    }

    @Test
    void putReplacesEveryBudgetAndAnEmptyListRemovesThem() throws Exception {
        replace(alice, "{\"budgets\": [{\"category_group\": \"Food\", \"monthly_amount\": \"1000\"},"
                + " {\"category_group\": \"Travel\", \"monthly_amount\": \"500\"}]}");
        replace(alice, "{\"budgets\": [{\"category_group\": \"Food\", \"monthly_amount\": \"1200\"}]}")
                .andExpect(jsonPath("$.budgets", hasSize(1)))
                .andExpect(jsonPath("$.budgets[0].category_group").value("Food"))
                .andExpect(jsonPath("$.budgets[0].budget").value("1200.00"));

        replace(alice, "{\"budgets\": []}").andExpect(status().isOk()).andExpect(jsonPath("$.budgets", empty()));
        categoryBudgets(alice, "")
                .andExpect(jsonPath("$.budgets", empty()))
                .andExpect(jsonPath("$.unbudgeted", hasSize(4)));
    }

    @Test
    void aZeroBudgetIsExceededByAnySpending() throws Exception {
        replace(alice, "{\"budgets\": [{\"category_group\": \"Food\", \"monthly_amount\": \"5000\"},"
                + " {\"category_group\": \"Shopping\", \"monthly_amount\": \"0\"}]}")
                .andExpect(jsonPath("$.budgets[0].category_group").value("Shopping"))
                .andExpect(jsonPath("$.budgets[0].status").value("EXCEEDED"))
                .andExpect(jsonPath("$.budgets[0].percentage_used").value(nullValue()));
    }

    @Test
    void rejectsUnknownAndRepeatedGroupsAndInvalidAmounts() throws Exception {
        replace(alice, """
                {"budgets": [{"category_group": "Food", "monthly_amount": "100"},
                             {"category_group": "Rockets", "monthly_amount": "100"},
                             {"category_group": "Food", "monthly_amount": "200"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", contains("budgets[1].category_group", "budgets[2].category_group")))
                .andExpect(jsonPath("$.errors[1].message").value("is repeated"));

        replace(alice, "{\"budgets\": [{\"category_group\": \"Food\", \"monthly_amount\": \"-5\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("budgets[0].monthly_amount"));
        replace(alice, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("budgets"));

        // Nothing was saved.
        categoryBudgets(alice, "").andExpect(jsonPath("$.budgets", empty()));
    }

    @Test
    void anotherUsersAccountIsNotFound() throws Exception {
        TestUser bob = users.create("bob", UserRole.CLIENT, true);

        categoryBudgets(bob, "").andExpect(status().isNotFound());
        replace(bob, "{\"budgets\": []}").andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnInvalidMonthAndUnknownParameters() throws Exception {
        categoryBudgets(alice, "?month=2026-13").andExpect(status().isBadRequest());
        categoryBudgets(alice, "?from=2026-01").andExpect(status().isBadRequest());
    }

    private ResultActions categoryBudgets(TestUser user, String query) throws Exception {
        return mvc.perform(get("/api/v1/accounts/" + card + "/category-budgets" + query).with(as(user)));
    }

    private ResultActions replace(TestUser user, String json) throws Exception {
        return mvc.perform(put("/api/v1/accounts/" + card + "/category-budgets").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
