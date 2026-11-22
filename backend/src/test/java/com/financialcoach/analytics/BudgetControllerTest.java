package com.financialcoach.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class BudgetControllerTest extends ApiIntegrationTest {

    @Autowired
    private Clock clock;

    private AnalyticsTestData data;
    private TestUser alice;
    private UUID card;

    @BeforeEach
    void createData() {
        data = new AnalyticsTestData(jdbc);
        alice = users.create("alice", UserRole.CLIENT, true);
        card = data.account(alice, "Debit card", "5000.00");
    }

    @Test
    void usageOfTheMonth() throws Exception {
        data.expense(alice, card, "2026-01-01T00:00:00", "1000.00", "Food");   // first second of the month
        data.expense(alice, card, "2026-01-31T23:59:59", "2750.00", "Travel"); // last second of the month
        data.income(alice, card, "2026-01-15T09:00:00", "20000.00", "Salary"); // income does not offset spending
        data.expense(alice, card, "2025-12-31T23:59:59", "800.00", "Food");    // other months are ignored
        data.expense(alice, card, "2026-02-01T00:00:00", "800.00", "Food");
        data.deletedExpense(alice, card, "2026-01-10T10:00:00", "500.00", "Food");

        budget(alice, card, "?month=2026-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(card.toString()))
                .andExpect(jsonPath("$.month").value("2026-01"))
                .andExpect(jsonPath("$.budget").value("5000.00"))
                .andExpect(jsonPath("$.spent").value("3750.00"))
                .andExpect(jsonPath("$.remaining").value("1250.00"))
                .andExpect(jsonPath("$.percentage_used").value("75.00"))
                .andExpect(jsonPath("$.status").value("ON_TRACK"));
    }

    @ParameterizedTest(name = "{0} of 5000 -> {2}")
    @CsvSource({
            "0.00,    0.00,   ON_TRACK, 5000.00",
            "3999.99, 80.00,  ON_TRACK, 1000.01", // 79.9998 % rounds to 80.00 but is still below 80
            "4000.00, 80.00,  WARNING,  1000.00",
            "5000.00, 100.00, WARNING,  0.00",
            "5000.01, 100.00, EXCEEDED, -0.01",
            "7500.00, 150.00, EXCEEDED, -2500.00",
    })
    void statusThresholds(String spent, String percentage, String expectedStatus, String remaining) throws Exception {
        if (!spent.equals("0.00")) {
            data.expense(alice, card, "2026-03-10T12:00:00", spent, "Food");
        }

        budget(alice, card, "?month=2026-03")
                .andExpect(jsonPath("$.spent").value(spent))
                .andExpect(jsonPath("$.percentage_used").value(percentage))
                .andExpect(jsonPath("$.remaining").value(remaining))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void accountWithoutBudget() throws Exception {
        UUID cash = data.account(alice, "Cash", null);
        data.expense(alice, cash, "2026-01-10T12:00:00", "300.00", "Food");

        budget(alice, cash, "?month=2026-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.spent").value("300.00"))
                .andExpect(jsonPath("$.remaining").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.percentage_used").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("NO_BUDGET"));
    }

    @Test
    void zeroBudgetIsExceededByAnySpending() throws Exception {
        UUID zero = data.account(alice, "Frozen", "0");
        data.expense(alice, zero, "2026-01-10T12:00:00", "0.01", "Food");

        budget(alice, zero, "?month=2026-01")
                .andExpect(jsonPath("$.budget").value("0.00"))
                .andExpect(jsonPath("$.remaining").value("-0.01"))
                .andExpect(jsonPath("$.percentage_used").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("EXCEEDED"));
    }

    @Test
    void zeroBudgetWithoutSpendingIsOnTrack() throws Exception {
        UUID zero = data.account(alice, "Frozen", "0");

        budget(alice, zero, "?month=2026-01")
                .andExpect(jsonPath("$.percentage_used").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("ON_TRACK"));
    }

    @Test
    void defaultsToTheCurrentMonth() throws Exception {
        budget(alice, card, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "2026-01-15", "january"})
    void invalidMonth(String month) throws Exception {
        budget(alice, card, "?month=" + month)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("month"));
    }

    @Test
    void unknownParametersAreRejected() throws Exception {
        budget(alice, card, "?month=2026-01&year=2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("year"));
    }

    @Test
    void anotherUsersAccountIsNotFound() throws Exception {
        TestUser bob = users.create("bob", UserRole.CLIENT, true);

        budget(bob, card, "?month=2026-01")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidAccountId() throws Exception {
        mvc.perform(get("/api/v1/accounts/not-a-uuid/budget").with(as(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("account_id"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/accounts/%s/budget".formatted(card))).andExpect(status().isUnauthorized());
    }

    private ResultActions budget(TestUser user, UUID accountId, String query) throws Exception {
        return mvc.perform(get("/api/v1/accounts/%s/budget%s".formatted(accountId, query)).with(as(user)));
    }
}
