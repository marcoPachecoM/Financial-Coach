package com.financialcoach.analytics;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET /accounts/{id}/alerts}. "Today" is 2026-11-21. */
@Import(FixedClockConfiguration.class)
class AlertControllerTest extends ApiIntegrationTest {

    private static final String CSV_HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes\n";

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
        card = data.account(alice, "Debit card", "1000.00");
    }

    @Test
    void noAlertsWhenEverythingIsFine() throws Exception {
        data.expense(alice, card, "2026-11-02T10:00:00", "300.00", "Food");

        alerts("").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void budgetCloseToTheLimit() throws Exception {
        data.expense(alice, card, "2026-11-02T10:00:00", "850.00", "Entertainment");

        alerts("")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("BUDGET_WARNING:2026-11"))
                .andExpect(jsonPath("$[0].type").value("BUDGET_WARNING"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].action").value(nullValue()))
                .andExpect(jsonPath("$[0].data.month").value("2026-11"))
                .andExpect(jsonPath("$[0].data.budget").value("1000.00"))
                .andExpect(jsonPath("$[0].data.spent").value("850.00"))
                .andExpect(jsonPath("$[0].data.remaining").value("150.00"))
                .andExpect(jsonPath("$[0].data.percentage_used").value("85.00"));
    }

    @Test
    void categoryBudgetsCloseToOrOverTheirLimit() throws Exception {
        jdbc.update("""
                INSERT INTO category_budgets (account_id, category_group, monthly_amount)
                VALUES (?, 'Food', 200), (?, 'Entertainment', 500), (?, 'Travel', 500)
                """, card, card, card);
        data.expense(alice, card, "2026-11-02T10:00:00", "250.00", "Food");          // 125 %
        data.expense(alice, card, "2026-11-03T10:00:00", "420.00", "Entertainment"); // 84 %
        data.expense(alice, card, "2026-11-04T10:00:00", "100.00", "Travel");        // 20 %: no alert

        alerts("")
                .andExpect(jsonPath("$[*].type", contains("CATEGORY_BUDGET_EXCEEDED", "CATEGORY_BUDGET_WARNING")))
                .andExpect(jsonPath("$[0].id").value("CATEGORY_BUDGET_EXCEEDED:2026-11:Food"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].data.category_group").value("Food"))
                .andExpect(jsonPath("$[0].data.budget").value("200.00"))
                .andExpect(jsonPath("$[0].data.spent").value("250.00"))
                .andExpect(jsonPath("$[0].data.remaining").value("-50.00"))
                .andExpect(jsonPath("$[0].data.percentage_used").value("125.00"))
                .andExpect(jsonPath("$[1].id").value("CATEGORY_BUDGET_WARNING:2026-11:Entertainment"))
                .andExpect(jsonPath("$[1].severity").value("WARNING"));
    }

    @Test
    void budgetExceededInAnotherMonth() throws Exception {
        data.expense(alice, card, "2026-05-02T10:00:00", "1200.00", "Entertainment");

        alerts("?month=2026-05")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].data.remaining").value("-200.00"));
    }

    @Test
    void upcomingOccurrenceAndSubscriptionAudit() throws Exception {
        recurringExpense("Netflix", "2026-10-05T12:00", "199");
        recurringExpense("Spotify", "2026-10-10T08:00", "129");
        recurringExpense("Gym", "2026-10-25T07:00", "450");

        alerts("")
                .andExpect(jsonPath("$[*].type", contains("RECURRING_UPCOMING", "SUBSCRIPTION_AUDIT")))
                .andExpect(jsonPath("$[0].severity").value("INFO"))
                .andExpect(jsonPath("$[0].data.name").value("Gym"))
                .andExpect(jsonPath("$[0].data.amount").value("450.00"))
                .andExpect(jsonPath("$[0].data.due_date").value("2026-11-25"))
                .andExpect(jsonPath("$[0].data.recurring_transaction_id").isNotEmpty())
                .andExpect(jsonPath("$[1].data.count").value(2))
                .andExpect(jsonPath("$[1].data.names", contains("Netflix", "Spotify")));
    }

    @Test
    void upcomingOnlyInTheCurrentMonth() throws Exception {
        recurringExpense("Gym", "2026-10-25T07:00", "450");

        alerts("?month=2026-10").andExpect(jsonPath("$[*].type", not(Matchers.hasItem("RECURRING_UPCOMING"))));
    }

    @Test
    void priceChangeReplacesTheAudit() throws Exception {
        recurringExpense("Netflix", "2026-10-05T12:00", "199");
        recurringExpense("Spotify", "2026-10-10T08:00", "129");

        upload("Expense,2026-11-06 03:10:00,03:10,NETFLIX.COM,219.00,Services,,Debit card,\n");

        alerts("")
                .andExpect(jsonPath("$[*].type", contains("RECURRING_PRICE_CHANGED")))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].action").value("UPDATE_RECURRING_AMOUNT"))
                .andExpect(jsonPath("$[0].data.name").value("Netflix"))
                .andExpect(jsonPath("$[0].data.expected_amount").value("199.00"))
                .andExpect(jsonPath("$[0].data.charged_amount").value("219.00"))
                .andExpect(jsonPath("$[0].data.change_percentage").value("10.05"))
                .andExpect(jsonPath("$[0].data.due_date").value("2026-11-05"));
    }

    @Test
    void generatedOccurrenceMissingFromTheBankStatement() throws Exception {
        recurringExpense("Netflix", "2026-10-05T12:00", "199");

        // The statement goes up to November 15 and has no Netflix charge.
        upload("Expense,2026-11-15 10:00:00,10:00,Oxxo,80.00,Food,,Debit card,\n");

        alerts("")
                .andExpect(jsonPath("$[0].type").value("RECURRING_MISSING"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].action").value("STOP_RECURRING"))
                .andExpect(jsonPath("$[0].data.name").value("Netflix"))
                .andExpect(jsonPath("$[0].data.due_date").value("2026-11-05"))
                .andExpect(jsonPath("$[0].data.transaction_id").isNotEmpty());
    }

    @Test
    void notMissingWhileTheStatementsDoNotCoverTheDate() throws Exception {
        recurringExpense("Netflix", "2026-10-05T12:00", "199");
        upload("Expense,2026-11-08 10:00:00,10:00,Oxxo,80.00,Food,,Debit card,\n");

        alerts("").andExpect(jsonPath("$[*].type", not(Matchers.hasItem("RECURRING_MISSING"))));
    }

    @Test
    void suggestsMakingASeriesRecurringUntilItIs() throws Exception {
        upload("""
                Expense,2026-08-12 07:00:00,07:00,Spotify,129.00,Services,,Debit card,
                Expense,2026-09-12 07:00:00,07:00,Spotify,129.00,Services,,Debit card,
                Expense,2026-10-12 07:00:00,07:00,Spotify,129.00,Services,,Debit card,
                Expense,2026-11-12 07:00:00,07:00,Spotify,129.00,Services,,Debit card,
                """);

        String body = alerts("")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("RECURRING_SUGGESTED"))
                .andExpect(jsonPath("$[0].action").value("CREATE_RECURRING"))
                .andExpect(jsonPath("$[0].data.name").value("Spotify"))
                .andExpect(jsonPath("$[0].data.amount").value("129.00"))
                .andExpect(jsonPath("$[0].data.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].data.occurrences").value(4))
                .andReturn().getResponse().getContentAsString();
        String transactionId = JsonPath.read(body, "$[0].data.transaction_id");

        mvc.perform(post("/api/v1/recurring-transactions").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_id\": \"%s\", \"frequency\": \"MONTHLY\"}".formatted(transactionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start_date").value("2026-11-12"));

        alerts("").andExpect(jsonPath("$[*].type", not(Matchers.hasItem("RECURRING_SUGGESTED"))));
    }

    @Test
    void mostSevereFirst() throws Exception {
        recurringExpense("Gym", "2026-10-25T07:00", "450");       // upcoming on 2026-11-25
        recurringExpense("Netflix", "2026-10-05T12:00", "199");   // generated on 2026-11-05...
        upload("Expense,2026-11-15 10:00:00,10:00,Oxxo,1100.00,Food,,Debit card,\n"); // ...missing; budget exceeded

        alerts("")
                .andExpect(jsonPath("$[*].type", contains("BUDGET_EXCEEDED", "RECURRING_MISSING", "RECURRING_UPCOMING")))
                .andExpect(jsonPath("$[*].severity", contains("CRITICAL", "WARNING", "INFO")));
    }

    @Test
    void anotherUsersAccountIsNotFound() throws Exception {
        TestUser bob = users.create("bob", UserRole.CLIENT, true);

        mvc.perform(get("/api/v1/accounts/" + card + "/alerts").with(as(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnInvalidMonthAndUnknownParameters() throws Exception {
        alerts("?month=2026-13").andExpect(status().isBadRequest());
        alerts("?from=2026-01").andExpect(status().isBadRequest());
    }

    private ResultActions alerts(String query) throws Exception {
        return mvc.perform(get("/api/v1/accounts/" + card + "/alerts" + query).with(as(alice)));
    }

    /** A monthly expense from {@code datetime}; occurrences up to today are created at once. */
    private void recurringExpense(String name, String datetime, String amount) throws Exception {
        mvc.perform(post("/api/v1/transactions").with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "EXPENSE", "datetime": "%s", "name": "%s", "amount": "%s",
                         "category_group": "Services", "account_id": "%s", "recurrence": {"frequency": "MONTHLY"}}
                        """.formatted(datetime, name, amount, card)))
                .andExpect(status().isCreated());
    }

    private void upload(String rows) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bank.csv", "text/csv", (CSV_HEADER + rows).getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/transactions/upload-csv").file(file).with(as(alice)))
                .andExpect(status().isCreated());
    }
}
