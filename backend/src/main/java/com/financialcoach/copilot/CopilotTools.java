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
import com.financialcoach.analytics.CategoryBudgetReport;
import com.financialcoach.analytics.CategoryBudgetService;
import com.financialcoach.analytics.CategoryBudgetUsage;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.transactions.CategoryGroupTotal;
import com.financialcoach.transactions.RecurringOverview;
import com.financialcoach.transactions.RecurringOverview.UpcomingCharge;
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
    private final CategoryBudgetService categoryBudgetService;
    private final Clock clock;
    private final UUID userId;
    private final UUID accountId;

    CopilotTools(TransactionStatistics statistics, RecurringReport recurringReport, AccountService accountService,
                 CategoryBudgetService categoryBudgetService, Clock clock, UUID userId, UUID accountId) {
        this.statistics = statistics;
        this.recurringReport = recurringReport;
        this.accountService = accountService;
        this.categoryBudgetService = categoryBudgetService;
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
            Monthly budget of the account for one month: budget, spent, remaining and percentage used,
            plus the budgets per category group when the account has any (e.g. "how am I doing with
            my food budget?"). Only expenses count. Use it for budget questions.""")
    String budgetStatus(@ToolParam(description = "Month, yyyy-MM. Usually the current month.") String month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return "Invalid month '%s'; use yyyy-MM.".formatted(month);
        }
        BigDecimal spent = statistics.totals(userId, accountId, DateRange.of(yearMonth)).expenseTotal();
        Optional<BigDecimal> budget = accountService.monthlyBudget(userId, accountId);
        String account;
        if (budget.isEmpty()) {
            account = "The account has no monthly budget. Spent in %s: %s.".formatted(yearMonth, money(spent));
        } else {
            BigDecimal limit = budget.get();
            account = "Month %s. Budget: %s. Spent: %s. Remaining: %s. Used: %s.".formatted(
                    yearMonth, money(limit), money(spent), money(limit.subtract(spent)), percent(spent, limit));
        }
        return account + categoryBudgets(yearMonth);
    }

    /** Lines of the category budgets, most used first; empty when the account has none. */
    private String categoryBudgets(YearMonth month) {
        CategoryBudgetReport report = categoryBudgetService.usage(userId, accountId, month);
        if (report.budgets().isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("\nCategory budgets:");
        for (CategoryBudgetUsage usage : report.budgets()) {
            out.append("\n- %s: spent %s of %s (%s, %s)".formatted(usage.categoryGroup().label(),
                    money(usage.spent()), money(usage.budget()), percent(usage.spent(), usage.budget()),
                    usage.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        }
        BigDecimal withoutBudget = report.unbudgeted().stream()
                .map(CategoryGroupTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        out.append("\nSpent in groups without a budget: %s.".formatted(money(withoutBudget)));
        return out.toString();
    }

    private static String percent(BigDecimal spent, BigDecimal limit) {
        return limit.signum() == 0 ? "n/a"
                : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP) + "%";
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

    @Tool(description = """
            Projection of this month's total expenses, computed from the account's data: spent so far,
            recurring charges still due this month and the daily average of the other expenses for the
            days left. Use it for "how much will I spend this month", forecast or "will I go over
            budget" questions. The numbers are already computed: report them, do not recalculate.""")
    String monthForecast() {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        LocalDate lastDay = month.atEndOfMonth();
        DateRange soFar = DateRange.between(month.atDay(1), today);

        BigDecimal spent = statistics.totals(userId, accountId, soFar).expenseTotal();
        BigDecimal recurringSpent = statistics.recurringExpenseTotal(userId, accountId, soFar);
        BigDecimal otherSpent = spent.subtract(recurringSpent);
        int daysElapsed = today.getDayOfMonth();
        int daysLeft = month.lengthOfMonth() - daysElapsed;

        // Other expenses (not recurring) keep their pace so far; recurring ones are known exactly.
        BigDecimal dailyAverage = otherSpent.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        BigDecimal otherAhead = dailyAverage.multiply(BigDecimal.valueOf(daysLeft));
        List<UpcomingCharge> pending = recurringReport.pendingExpenses(userId, accountId, lastDay);
        BigDecimal pendingTotal = pending.stream().map(UpcomingCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal forecast = spent.add(pendingTotal).add(otherAhead);

        StringBuilder out = new StringBuilder("Forecast for %s (today %s, %d days left): %s.\n".formatted(
                month, today, daysLeft, money(forecast)));
        out.append("- Spent so far: %s (%s recurring, %s other).\n".formatted(
                money(spent), money(recurringSpent), money(otherSpent)));
        out.append("- Other expenses average %s per day: %s more over the days left.\n".formatted(
                money(dailyAverage), money(otherAhead)));
        out.append(pending.isEmpty()
                ? "- No recurring charges still due this month.\n"
                : "- Recurring charges still due: %s (%s).\n".formatted(money(pendingTotal), pending.stream()
                        .map(c -> "%s %s on %s".formatted(c.name(), money(c.amount()), c.dueDate()))
                        .collect(Collectors.joining("; "))));
        accountService.monthlyBudget(userId, accountId).ifPresent(budget -> {
            BigDecimal difference = budget.subtract(forecast);
            out.append(difference.signum() >= 0
                    ? "- Budget %s: about %s left at month end.\n".formatted(money(budget), money(difference))
                    : "- Budget %s: about %s over at month end.\n".formatted(money(budget), money(difference.negate())));
        });
        BigDecimal lastMonth = statistics.totals(userId, accountId, DateRange.of(month.minusMonths(1))).expenseTotal();
        out.append("- Last month's expenses: %s.\n".formatted(money(lastMonth)));
        if (spent.signum() == 0) {
            out.append("No expenses recorded this month yet: the forecast only has the recurring charges.");
        } else if (daysElapsed < 7) {
            out.append("Only %d days into the month: a rough estimate.".formatted(daysElapsed));
        }
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
