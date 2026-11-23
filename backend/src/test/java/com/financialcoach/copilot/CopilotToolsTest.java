package com.financialcoach.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.transactions.RecurringReport;
import com.financialcoach.transactions.TransactionStatistics;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Calls the tools directly against PostgreSQL: what the LLM receives, without calling the LLM. */
@Import(FixedClockConfiguration.class)
class CopilotToolsTest extends ApiIntegrationTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    @Autowired
    private TransactionStatistics statistics;

    @Autowired
    private RecurringReport recurringReport;

    @Autowired
    private AccountService accountService;

    @Autowired
    private Clock clock;

    private TestUser user;
    private UUID accountId;
    private CopilotTools tools;

    @BeforeEach
    void setUp() {
        user = users.client();
        accountId = account(user, "Debit", "1000.00");
        tools = toolsFor(user, accountId);

        expense("2026-09-05T20:00", "Cinemex", "150.00", "Entertainment", "Movies");
        expense("2026-09-12T21:00", "Netflix", "199.00", "Entertainment", "Streaming");
        expense("2026-09-15T13:00", "Uber Eats", "320.50", "Food", null);
        expense("2026-10-02T09:00", "Uber", "80.00", "Travel", null);
        income("2026-09-30T08:00", "ACME payroll", "25000.00", "Salary");
    }

    @Test
    void summaryReportsTotalsOfTheRange() {
        assertThat(tools.summary(SEP_1, SEP_30))
                .isEqualTo("Expenses: 669.50 (3 transactions, largest 320.50). Income: 25000.00 (1 transactions).");
    }

    @Test
    void spendingByCategoryListsGroupsLargestFirst() {
        assertThat(tools.spendingByCategory(SEP_1, SEP_30))
                .isEqualTo("Entertainment: 349.00 (2 transactions)\nFood: 320.50 (1 transactions)");
    }

    @Test
    void searchMatchesMerchantTextCaseInsensitively() {
        String result = tools.searchTransactions(SEP_1, LocalDate.of(2026, 10, 31), "UBER", null);

        assertThat(result).startsWith("2 matches. Expenses: 400.50 (2). Income: 0.00 (0).");
        assertThat(result).contains("2026-10-02 | EXPENSE | Uber | 80.00 | Travel");
        assertThat(result).contains("Uber Eats | 320.50 | Food");
    }

    @Test
    void searchFiltersByCategoryGroupAndCategoryText() {
        assertThat(tools.searchTransactions(SEP_1, SEP_30, null, "entertainment")).startsWith("2 matches.");
        assertThat(tools.searchTransactions(SEP_1, SEP_30, "stream", null)).startsWith("1 matches.").contains("Netflix");
    }

    @Test
    void searchTreatsLikeWildcardsAsText() {
        assertThat(tools.searchTransactions(SEP_1, SEP_30, "%", null)).isEqualTo("No matching transactions.");
    }

    @Test
    void searchExplainsAnUnknownCategoryGroup() {
        assertThat(tools.searchTransactions(SEP_1, SEP_30, null, "Movies"))
                .startsWith("Unknown category group 'Movies'. Valid groups:")
                .contains("Entertainment");
    }

    @Test
    void searchReturnsOnlyTheNewestRowsButTotalsEveryMatch() {
        for (int day = 1; day <= 25; day++) {
            expense("2026-08-%02dT12:00".formatted(day), "Oxxo", "10.00", "Snacks", null);
        }
        String result = tools.searchTransactions(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "oxxo", null);

        assertThat(result).startsWith("25 matches. Expenses: 250.00 (25). Income: 0.00 (0). Newest 20:");
        assertThat(result.lines().count()).isEqualTo(1 + CopilotTools.SEARCH_LIMIT);
        assertThat(result.lines().skip(1).findFirst()).hasValueSatisfying(row -> assertThat(row).startsWith("2026-08-25"));
    }

    @Test
    void invalidRangeIsReportedToTheModel() {
        assertThat(tools.summary(SEP_30, SEP_1)).isEqualTo("Invalid range: startDate is after endDate.");
    }

    @Test
    void budgetStatusComparesSpentWithTheMonthlyBudget() {
        assertThat(tools.budgetStatus("2026-09"))
                .isEqualTo("Month 2026-09. Budget: 1000.00. Spent: 669.50. Remaining: 330.50. Used: 67.0%.");
        assertThat(tools.budgetStatus("September")).isEqualTo("Invalid month 'September'; use yyyy-MM.");
    }

    @Test
    void budgetStatusWithoutBudget() {
        UUID cash = account(user, "Cash", null);
        assertThat(toolsFor(user, cash).budgetStatus("2026-09"))
                .isEqualTo("The account has no monthly budget. Spent in 2026-09: 0.00.");
    }

    @Test
    void recurringChargesListsActiveRules() {
        assertThat(tools.recurringCharges()).isEqualTo("No active recurring transactions.");

        rule("Spotify", "129.00", "MONTHLY", LocalDate.of(2026, 12, 3));
        rule("Old gym", "500.00", "MONTHLY", null);

        assertThat(tools.recurringCharges())
                .startsWith("Active recurring transactions:\n- Spotify | EXPENSE | 129.00 | monthly | next 2026-12-03")
                .doesNotContain("Old gym");
    }

    @Test
    void monthForecastAddsWhatIsSpentWhatIsDueAndThePaceOfTheRest() {
        // Today is 2026-11-21: 21 days elapsed, 9 left.
        UUID rent = rule("Rent", "500.00", "MONTHLY", LocalDate.of(2026, 12, 1));
        recurringExpense(rent, "2026-11-01T09:00", "Rent", "500.00");
        expense("2026-11-03T12:00", "Oxxo", "100.00", "Food", null);
        expense("2026-11-20T19:00", "Cinemex", "110.00", "Entertainment", null);
        rule("Spotify", "129.00", "MONTHLY", LocalDate.of(2026, 11, 28));
        rule("Gym", "50.00", "WEEKLY", LocalDate.of(2026, 11, 21)); // today's not created yet: due twice
        rule("Old gym", "500.00", "MONTHLY", null);

        // 710 spent + 229 recurring still due + 10.00 a day of other expenses × 9 days = 1029.
        assertThat(tools.monthForecast()).isEqualTo("""
                Forecast for 2026-11 (today 2026-11-21, 9 days left): 1029.00.
                - Spent so far: 710.00 (500.00 recurring, 210.00 other).
                - Other expenses average 10.00 per day: 90.00 more over the days left.
                - Recurring charges still due: 229.00 (Gym 50.00 on 2026-11-21; Gym 50.00 on 2026-11-28; Spotify 129.00 on 2026-11-28).
                - Budget 1000.00: about 29.00 over at month end.
                - Last month's expenses: 80.00.""");
    }

    @Test
    void monthForecastWithoutExpensesThisMonthSaysSo() {
        UUID cash = account(user, "Cash", null);

        assertThat(toolsFor(user, cash).monthForecast()).isEqualTo("""
                Forecast for 2026-11 (today 2026-11-21, 9 days left): 0.00.
                - Spent so far: 0.00 (0.00 recurring, 0.00 other).
                - Other expenses average 0.00 per day: 0.00 more over the days left.
                - No recurring charges still due this month.
                - Last month's expenses: 0.00.
                No expenses recorded this month yet: the forecast only has the recurring charges.""");
    }

    @Test
    void toolsNeverReadAnotherUsersAccount() {
        TestUser other = users.create("other@example.com", user.role(), true);
        CopilotTools foreign = toolsFor(other, accountId);

        assertThatThrownBy(() -> foreign.summary(SEP_1, SEP_30)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(foreign::recurringCharges).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(foreign::monthForecast).isInstanceOf(NotFoundException.class);
    }

    private CopilotTools toolsFor(TestUser owner, UUID account) {
        return new CopilotTools(statistics, recurringReport, accountService, clock, owner.id(), account);
    }

    private UUID account(TestUser owner, String name, String monthlyBudget) {
        return jdbc.queryForObject(
                "INSERT INTO accounts (user_id, name, monthly_budget) VALUES (?, ?, ?) RETURNING id",
                UUID.class, owner.id(), name, monthlyBudget == null ? null : new BigDecimal(monthlyBudget));
    }

    private void expense(String datetime, String name, String amount, String group, String category) {
        insert("EXPENSE", datetime, name, new BigDecimal(amount), group, category);
    }

    /** Income is stored negative. */
    private void income(String datetime, String name, String amount, String group) {
        insert("INCOME", datetime, name, new BigDecimal(amount).negate(), group, null);
    }

    private void insert(String type, String datetime, String name, BigDecimal amount, String group, String category) {
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, datetime, name, amount, category_group, category,
                                          source, category_group_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', 'USER')
                """, user.id(), accountId, type, LocalDateTime.parse(datetime), name, amount, group, category);
    }

    /** {@code nextDueDate} null means the rule has ended. Starts on Saturday 2026-01-03. */
    private UUID rule(String name, String amount, String frequency, LocalDate nextDueDate) {
        return jdbc.queryForObject("""
                INSERT INTO recurring_transactions (user_id, account_id, type, name, amount, category_group,
                                                    frequency, start_date, time_of_day, next_due_date)
                VALUES (?, ?, 'EXPENSE', ?, ?, 'Entertainment', ?, DATE '2026-01-03', TIME '09:00', ?)
                RETURNING id
                """, UUID.class, user.id(), accountId, name, new BigDecimal(amount), frequency, nextDueDate);
    }

    /** An occurrence already created by a rule. */
    private void recurringExpense(UUID ruleId, String datetime, String name, String amount) {
        LocalDateTime at = LocalDateTime.parse(datetime);
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, datetime, name, amount, category_group,
                                          source, category_group_source, recurring_transaction_id, recurrence_date)
                VALUES (?, ?, 'EXPENSE', ?, ?, ?, 'House', 'RECURRING', 'USER', ?, ?)
                """, user.id(), accountId, at, name, new BigDecimal(amount), ruleId, at.toLocalDate());
    }
}
