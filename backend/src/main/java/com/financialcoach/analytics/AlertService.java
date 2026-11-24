package com.financialcoach.analytics;

import com.financialcoach.shared.Money;
import com.financialcoach.transactions.RecurringOverview;
import com.financialcoach.transactions.RecurringOverview.AuditedCharge;
import com.financialcoach.transactions.RecurringOverview.MissingCharge;
import com.financialcoach.transactions.RecurringOverview.PriceChange;
import com.financialcoach.transactions.RecurringOverview.Suggestion;
import com.financialcoach.transactions.RecurringOverview.UpcomingCharge;
import com.financialcoach.transactions.RecurringReport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alerts of an account for a month, computed on every request from the budget and the recurring
 * transactions (nothing is stored). Deterministic rules, no AI: the copilot can later use this
 * list as one of its tools.
 */
@Service
class AlertService {

    private final BudgetService budgetService;
    private final CategoryBudgetService categoryBudgetService;
    private final RecurringReport recurringReport;
    private final Clock clock;

    AlertService(BudgetService budgetService, CategoryBudgetService categoryBudgetService,
                 RecurringReport recurringReport, Clock clock) {
        this.budgetService = budgetService;
        this.categoryBudgetService = categoryBudgetService;
        this.recurringReport = recurringReport;
        this.clock = clock;
    }

    /** Most severe first; within a severity, in the order of {@link AlertType}. */
    @Transactional(readOnly = true)
    List<AlertResponse> alerts(UUID userId, UUID accountId, YearMonth month) {
        BudgetUsageResponse budget = budgetService.usage(userId, accountId, month);
        RecurringOverview recurring = recurringReport.report(userId, accountId, month, LocalDate.now(clock));

        List<AlertResponse> alerts = new ArrayList<>();
        budgetAlert(budget).ifPresent(alerts::add);
        categoryBudgetService.usage(userId, accountId, month).budgets()
                .forEach(usage -> categoryBudgetAlert(month, usage).ifPresent(alerts::add));
        recurring.missing().forEach(charge -> alerts.add(missing(charge)));
        recurring.priceChanges().forEach(change -> alerts.add(priceChanged(change)));
        recurring.upcoming().forEach(charge -> alerts.add(upcoming(charge)));
        if (recurring.priceChanges().isEmpty() && !recurring.audited().isEmpty()) {
            alerts.add(audit(month, recurring.audited()));
        }
        recurring.suggestions().forEach(suggestion -> alerts.add(suggested(suggestion)));

        alerts.sort(Comparator.comparing(AlertResponse::severity).thenComparing(AlertResponse::type));
        return alerts;
    }

    private static Optional<AlertResponse> budgetAlert(BudgetUsageResponse budget) {
        AlertType type = switch (budget.status()) {
            case EXCEEDED -> AlertType.BUDGET_EXCEEDED;
            case WARNING -> AlertType.BUDGET_WARNING;
            case ON_TRACK, NO_BUDGET -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        Map<String, Object> data = data(
                "month", budget.month(),
                "budget", budget.budget(),
                "spent", budget.spent(),
                "remaining", budget.remaining(),
                "percentage_used", budget.percentageUsed());
        AlertSeverity severity = type == AlertType.BUDGET_EXCEEDED ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        return Optional.of(new AlertResponse(type + ":" + budget.month(), type, severity, data, null));
    }

    /** Same thresholds as the account's budget; the id names the group, one alert per group. */
    private static Optional<AlertResponse> categoryBudgetAlert(YearMonth month, CategoryBudgetUsage usage) {
        AlertType type = switch (usage.status()) {
            case EXCEEDED -> AlertType.CATEGORY_BUDGET_EXCEEDED;
            case WARNING -> AlertType.CATEGORY_BUDGET_WARNING;
            case ON_TRACK, NO_BUDGET -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        Map<String, Object> data = data(
                "month", month.toString(),
                "category_group", usage.categoryGroup().label(),
                "budget", Money.format(usage.budget()),
                "spent", Money.format(usage.spent()),
                "remaining", Money.format(usage.remaining()),
                "percentage_used", usage.percentageUsed() == null ? null : usage.percentageUsed().toPlainString());
        AlertSeverity severity = type == AlertType.CATEGORY_BUDGET_EXCEEDED ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        String id = type + ":" + month + ":" + usage.categoryGroup().label();
        return Optional.of(new AlertResponse(id, type, severity, data, null));
    }

    private static AlertResponse missing(MissingCharge charge) {
        Map<String, Object> data = data(
                "transaction_id", charge.transactionId(),
                "recurring_transaction_id", charge.recurringTransactionId(),
                "name", charge.name(),
                "amount", Money.format(charge.amount()),
                "due_date", charge.dueDate());
        return new AlertResponse("RECURRING_MISSING:" + charge.transactionId(), AlertType.RECURRING_MISSING,
                AlertSeverity.WARNING, data, AlertAction.STOP_RECURRING);
    }

    private static AlertResponse priceChanged(PriceChange change) {
        Map<String, Object> data = data(
                "recurring_transaction_id", change.recurringTransactionId(),
                "transaction_id", change.transactionId(),
                "name", change.name(),
                "expected_amount", Money.format(change.expectedAmount()),
                "charged_amount", Money.format(change.chargedAmount()),
                // By absolute value: positive means "costs more" for expenses, "pays more" for income.
                "change_percentage", Percentages.of(
                        change.chargedAmount().abs().subtract(change.expectedAmount().abs()),
                        change.expectedAmount().abs()).toPlainString(),
                "due_date", change.dueDate());
        String id = "RECURRING_PRICE_CHANGED:" + change.transactionId() + ":" + Money.format(change.chargedAmount())
                + ":" + Money.format(change.expectedAmount());
        return new AlertResponse(id, AlertType.RECURRING_PRICE_CHANGED, AlertSeverity.WARNING, data,
                AlertAction.UPDATE_RECURRING_AMOUNT);
    }

    private static AlertResponse upcoming(UpcomingCharge charge) {
        Map<String, Object> data = data(
                "recurring_transaction_id", charge.recurringTransactionId(),
                "name", charge.name(),
                "amount", Money.format(charge.amount()),
                "due_date", charge.dueDate());
        return new AlertResponse("RECURRING_UPCOMING:" + charge.recurringTransactionId() + ":" + charge.dueDate(),
                AlertType.RECURRING_UPCOMING, AlertSeverity.INFO, data, null);
    }

    private static AlertResponse audit(YearMonth month, List<AuditedCharge> audited) {
        List<String> names = audited.stream().map(AuditedCharge::name).toList();
        Map<String, Object> data = data(
                "month", month.toString(),
                "count", audited.size(),
                "names", names);
        return new AlertResponse("SUBSCRIPTION_AUDIT:" + month + ":" + audited.size(), AlertType.SUBSCRIPTION_AUDIT,
                AlertSeverity.INFO, data, null);
    }

    private static AlertResponse suggested(Suggestion suggestion) {
        Map<String, Object> data = data(
                "transaction_id", suggestion.transactionId(),
                "name", suggestion.name(),
                "amount", Money.format(suggestion.amount()),
                "frequency", suggestion.frequency(),
                "occurrences", suggestion.occurrences());
        return new AlertResponse("RECURRING_SUGGESTED:" + suggestion.transactionId(), AlertType.RECURRING_SUGGESTED,
                AlertSeverity.INFO, data, AlertAction.CREATE_RECURRING);
    }

    /** Ordered map from key/value pairs; null values are kept (the field is present, null). */
    private static Map<String, Object> data(Object... keysAndValues) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            data.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return data;
    }
}
