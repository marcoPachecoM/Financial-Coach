package com.financialcoach.analytics;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import com.financialcoach.transactions.CategoryGroupTotal;
import com.financialcoach.transactions.TransactionStatistics;
import com.financialcoach.transactions.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly budgets per category group of an account (opt-in: only the groups the user watches).
 * Public for the copilot. Only expenses count, as in the account's budget.
 */
@Service
public class CategoryBudgetService {

    /** Above any real ratio: a zero budget with spending sorts first. */
    private static final BigDecimal OVER_ZERO_BUDGET = BigDecimal.valueOf(Long.MAX_VALUE);

    private final CategoryBudgetRepository budgets;
    private final AccountService accountService;
    private final TransactionStatistics statistics;

    CategoryBudgetService(CategoryBudgetRepository budgets, AccountService accountService,
                          TransactionStatistics statistics) {
        this.budgets = budgets;
        this.accountService = accountService;
        this.statistics = statistics;
    }

    /** 404 unless the account belongs to the user. */
    @Transactional(readOnly = true)
    public CategoryBudgetReport usage(UUID userId, UUID accountId, YearMonth month) {
        accountService.requireOwned(userId, accountId);
        Map<CategoryGroup, CategoryGroupTotal> spentByGroup = new EnumMap<>(CategoryGroup.class);
        List<CategoryGroupTotal> expenses = statistics.totalsByCategoryGroup(
                userId, accountId, TransactionType.EXPENSE, DateRange.of(month));
        expenses.forEach(total -> spentByGroup.put(total.categoryGroup(), total));

        List<CategoryBudgetUsage> usages = new ArrayList<>();
        for (CategoryBudget budget : budgets.findByAccountId(accountId)) {
            CategoryGroupTotal total = spentByGroup.get(budget.getCategoryGroup());
            BigDecimal spent = total == null ? BigDecimal.ZERO : total.amount();
            usages.add(new CategoryBudgetUsage(budget.getCategoryGroup(), budget.getMonthlyAmount(), spent,
                    BudgetStatus.of(spent, budget.getMonthlyAmount())));
        }
        // Closest to (or furthest over) the limit first; ties by group name.
        usages.sort(Comparator.comparing(CategoryBudgetService::usedRatio).reversed()
                .thenComparing(usage -> usage.categoryGroup().label()));

        List<CategoryGroupTotal> unbudgeted = expenses.stream()
                .filter(total -> usages.stream().noneMatch(usage -> usage.categoryGroup() == total.categoryGroup()))
                .toList();
        return new CategoryBudgetReport(month, List.copyOf(usages), unbudgeted);
    }

    /**
     * Replaces every budget of the account (groups left out lose theirs). Fails with 400 on an
     * unknown or repeated group, reporting every problem at once.
     */
    @Transactional
    CategoryBudgetReport replace(UUID userId, UUID accountId, List<CategoryBudgetsRequest.Item> items,
                                 YearMonth month) {
        accountService.requireOwned(userId, accountId);
        Map<CategoryGroup, BigDecimal> wanted = new EnumMap<>(CategoryGroup.class);
        List<ApiFieldError> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String field = "budgets[%d].category_group".formatted(i);
            CategoryGroup group;
            try {
                group = CategoryGroup.fromLabel(items.get(i).categoryGroup().strip());
            } catch (IllegalArgumentException e) {
                errors.add(ApiFieldError.of(field, "must be one of the category groups"));
                continue;
            }
            if (wanted.putIfAbsent(group, Money.parse(items.get(i).monthlyAmount())) != null) {
                errors.add(ApiFieldError.of(field, "is repeated"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        budgets.deleteByAccount(accountId);
        wanted.forEach((group, amount) -> budgets.save(new CategoryBudget(accountId, group, amount)));
        budgets.flush();
        return usage(userId, accountId, month);
    }

    private static BigDecimal usedRatio(CategoryBudgetUsage usage) {
        if (usage.budget().signum() == 0) {
            return usage.spent().signum() == 0 ? BigDecimal.ZERO : OVER_ZERO_BUDGET;
        }
        return usage.spent().divide(usage.budget(), 6, RoundingMode.HALF_UP);
    }
}
