package com.financialcoach.copilot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.transactions.CategoryGroupTotal;
import com.financialcoach.transactions.RecurringOverview;
import com.financialcoach.transactions.RecurringReport;
import com.financialcoach.transactions.RecurringRuleSummary;
import com.financialcoach.transactions.TransactionSearch;
import com.financialcoach.transactions.TransactionStatistics;
import com.financialcoach.transactions.TransactionTotals;
import com.financialcoach.transactions.TransactionType;

/**
 * Read-only queries the LLM can call. Built per request: the user and the account come from the
 * authenticated request, never from the model, so it cannot read another account's data.
 *
 * <p>Results are short plain text (amounts with 2 decimals): every token goes back to the model.
 * Invalid arguments return a message instead of failing, so the model can correct itself.
 */
class CopilotTools {

    /** Rows returned by the search; totals still cover every match. */
    static final int SEARCH_LIMIT = 20;

    private static final String GROUPS = Arrays.stream(CategoryGroup.values())
            .map(CategoryGroup::label).collect(Collectors.joining(", "));

    private final TransactionStatistics statistics;
    private final RecurringReport recurringReport;
    private final AccountService accountService;
    private final Clock clock;
    private final UUID userId;
    private final UUID accountId;

    CopilotTools(TransactionStatistics statistics, RecurringReport recurringReport, AccountService accountService,
                 Clock clock, UUID userId, UUID accountId) {
        this.statistics = statistics;
        this.recurringReport = recurringReport;
        this.accountService = accountService;
        this.clock = clock;
        this.userId = userId;
        this.accountId = accountId;
    }

    @Tool(description = """
            Total expenses and income of the account between two dates (inclusive), with the number
            of transactions and the largest expense. Use it for "how much did I spend/earn" questions.""")
    String summary(@ToolParam(description = "Start date, yyyy-MM-dd") LocalDate startDate,
                   @ToolParam(description = "End date (inclusive), yyyy-MM-dd") LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            return "Invalid range: startDate is after endDate.";
        }
        TransactionTotals totals = statistics.totals(userId, accountId, DateRange.between(startDate, endDate));
        return "Expenses: %s (%d transactions, largest %s). Income: %s (%d transactions).".formatted(
                money(totals.expenseTotal()), totals.expenseCount(), money(totals.largestExpense()),
                money(totals.incomeTotal()), totals.incomeCount());
    }

    @Tool(description = """
            Expenses per category group (e.g. Entertainment, Food, Travel) between two dates (inclusive),
            largest first. Use it for questions about a category or "what do I spend the most on".""")
    String spendingByCategory(@ToolParam(description = "Start date, yyyy-MM-dd") LocalDate startDate,
                              @ToolParam(description = "End date (inclusive), yyyy-MM-dd") LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            return "Invalid range: startDate is after endDate.";
        }
        List<CategoryGroupTotal> groups = statistics.totalsByCategoryGroup(userId, accountId, TransactionType.EXPENSE,
                DateRange.between(startDate, endDate));
        if (groups.isEmpty()) {
            return "No expenses between %s and %s.".formatted(startDate, endDate);
        }
        return groups.stream()
                .map(g -> "%s: %s (%d transactions)".formatted(g.categoryGroup().label(), money(g.amount()), g.count()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = """
            Finds transactions between two dates (inclusive) by merchant or category text and/or by
            category group. Returns the totals of all matches and the newest 20 of them.
            Use it for questions about a merchant (e.g. "Uber", "Netflix") or to list transactions.""")
    String searchTransactions(
            @ToolParam(description = "Start date, yyyy-MM-dd") LocalDate startDate,
            @ToolParam(description = "End date (inclusive), yyyy-MM-dd") LocalDate endDate,
            @ToolParam(description = "Text contained in the merchant name or category, e.g. uber. Omit to match all.",
                    required = false) String text,
            @ToolParam(description = "Exact category group, e.g. Entertainment. Omit for all groups.",
                    required = false) String categoryGroup) {
        if (startDate.isAfter(endDate)) {
            return "Invalid range: startDate is after endDate.";
        }
        Optional<CategoryGroup> group = Optional.empty();
        if (categoryGroup != null && !categoryGroup.isBlank()) {
            group = findGroup(categoryGroup);
            if (group.isEmpty()) {
                return "Unknown category group '%s'. Valid groups: %s.".formatted(categoryGroup, GROUPS);
            }
        }

        TransactionSearch result = statistics.search(userId, accountId, DateRange.between(startDate, endDate), text,
                group.orElse(null), SEARCH_LIMIT);
        if (result.matches() == 0) {
            return "No matching transactions.";
        }
        String header = "%d matches. Expenses: %s (%d). Income: %s (%d).%s".formatted(
                result.matches(), money(result.expenseTotal()), result.expenseCount(),
                money(result.incomeTotal()), result.incomeCount(),
                result.matches() > result.latest().size() ? " Newest %d:".formatted(result.latest().size()) : "");
        String rows = result.latest().stream()
                .map(t -> "%s | %s | %s | %s | %s".formatted(t.date(), t.type(), t.name(), money(t.amount()),
                        t.categoryGroup().label() + (t.category() == null ? "" : " / " + t.category())))
                .collect(Collectors.joining("\n"));
        return header + "\n" + rows;
    }

    @Tool(description = """
            Monthly budget of the account for one month: budget, spent, remaining and percentage used.
            Only expenses count. Use it for budget questions.""")
    String budgetStatus(@ToolParam(description = "Month, yyyy-MM. Usually the current month.") String month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return "Invalid month '%s'; use yyyy-MM.".formatted(month);
        }
        BigDecimal spent = statistics.totals(userId, accountId, DateRange.of(yearMonth)).expenseTotal();
        Optional<BigDecimal> budget = accountService.monthlyBudget(userId, accountId);
        if (budget.isEmpty()) {
            return "The account has no monthly budget. Spent in %s: %s.".formatted(yearMonth, money(spent));
        }
        BigDecimal limit = budget.get();
        String used = limit.signum() == 0 ? "n/a"
                : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP) + "%";
        return "Month %s. Budget: %s. Spent: %s. Remaining: %s. Used: %s.".formatted(
                yearMonth, money(limit), money(spent), money(limit.subtract(spent)), used);
    }

    @Tool(description = """
            Recurring transactions (subscriptions, rent, salary...) of the account: active rules with
            amount, frequency and next date, plus this month's findings (upcoming charges, missing
            charges, price changes, possible new subscriptions). Use it for subscription questions.""")
    String recurringCharges() {
        LocalDate today = LocalDate.now(clock);
        List<RecurringRuleSummary> rules = recurringReport.activeRules(userId, accountId);
        RecurringOverview month = recurringReport.report(userId, accountId, YearMonth.from(today), today);

        StringBuilder out = new StringBuilder();
        if (rules.isEmpty()) {
            out.append("No active recurring transactions.\n");
        } else {
            out.append("Active recurring transactions:\n");
            rules.forEach(r -> out.append("- %s | %s | %s | %s | next %s\n".formatted(
                    r.name(), r.type(), money(r.amount()), r.frequency().name().toLowerCase(Locale.ROOT),
                    r.nextDueDate())));
        }
        month.upcoming().forEach(c -> out.append("Upcoming: %s %s on %s\n".formatted(
                c.name(), money(c.amount().abs()), c.dueDate())));
        month.missing().forEach(c -> out.append("Missing from bank statements: %s %s due %s\n".formatted(
                c.name(), money(c.amount().abs()), c.dueDate())));
        month.priceChanges().forEach(c -> out.append("Price change: %s expected %s, charged %s on %s\n".formatted(
                c.name(), money(c.expectedAmount().abs()), money(c.chargedAmount().abs()), c.dueDate())));
        month.suggestions().forEach(s -> out.append("Looks recurring but not tracked: %s %s (%s, %d charges)\n".formatted(
                s.name(), money(s.amount().abs()), s.frequency().name().toLowerCase(Locale.ROOT), s.occurrences())));
        return out.toString().strip();
    }

    /** Case-insensitive, on the label ("Higiene personal") or the constant name (HIGIENE_PERSONAL). */
    private static Optional<CategoryGroup> findGroup(String value) {
        String wanted = value.strip();
        return Arrays.stream(CategoryGroup.values())
                .filter(g -> g.label().equalsIgnoreCase(wanted) || g.name().equalsIgnoreCase(wanted))
                .findFirst();
    }

    private static String money(BigDecimal amount) {
        return Money.format(amount);
    }
}
