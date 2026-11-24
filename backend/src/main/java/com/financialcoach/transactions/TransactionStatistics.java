package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates over an account's transactions, for other modules (analytics, budgets, later the
 * copilot). The aggregation runs in the database; soft-deleted rows are excluded.
 * Every method fails with 404 unless the account belongs to the user.
 */
@Service
public class TransactionStatistics {

    private final TransactionRepository transactions;
    private final TagRepository tagRepository;
    private final AccountService accountService;

    TransactionStatistics(TransactionRepository transactions, TagRepository tagRepository,
                          AccountService accountService) {
        this.transactions = transactions;
        this.tagRepository = tagRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public TransactionTotals totals(UUID userId, UUID accountId, DateRange range) {
        accountService.requireOwned(userId, accountId);

        TypeTotals expenses = empty(TransactionType.EXPENSE);
        TypeTotals income = empty(TransactionType.INCOME);
        for (TypeTotals totals : transactions.totalsByType(userId, accountId, range.from(), range.to())) {
            if (totals.type() == TransactionType.EXPENSE) {
                expenses = totals;
            } else {
                income = totals;
            }
        }
        return new TransactionTotals(
                expenses.count(), expenses.sum(), expenses.largest(),
                income.count(), income.sum().negate());
    }

    /** Expenses in the range that are occurrences of a recurring transaction (positive). */
    @Transactional(readOnly = true)
    public BigDecimal recurringExpenseTotal(UUID userId, UUID accountId, DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.recurringExpenseTotal(userId, accountId, range.from(), range.to());
    }

    /** One entry per category group with transactions of this type, largest amount first. */
    @Transactional(readOnly = true)
    public List<CategoryGroupTotal> totalsByCategoryGroup(UUID userId, UUID accountId, TransactionType type,
                                                          DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.totalsByCategoryGroup(userId, accountId, type, range.from(), range.to());
    }

    /**
     * One entry per day with transactions in the range, oldest first (for the calendar and the
     * spending trend). With {@code categoryGroup}, only that group counts.
     */
    @Transactional(readOnly = true)
    public List<DailyTotal> dailyTotals(UUID userId, UUID accountId, DateRange range, CategoryGroup categoryGroup) {
        accountService.requireOwned(userId, accountId);
        Collection<CategoryGroup> groups = categoryGroup == null
                ? Arrays.asList(CategoryGroup.values())
                : Set.of(categoryGroup);
        return transactions.dailyTotals(userId, accountId, range.from(), range.to(), groups, TransactionType.EXPENSE);
    }

    /** First and last transaction of the range (the dashboard's date presets are anchored to them). */
    @Transactional(readOnly = true)
    public DateSpan dateSpan(UUID userId, UUID accountId, DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.dateSpan(userId, accountId, range.from(), range.to());
    }

    /**
     * Transactions whose name or category contains {@code text} (case-insensitive) and, when
     * given, of {@code categoryGroup} and carrying {@code tag} (compared like tag names: case,
     * spaces and a leading "#" do not matter). Totals cover every match; only the newest
     * {@code limit} rows are returned. A null or blank {@code text} or {@code tag} matches all.
     */
    @Transactional(readOnly = true)
    public TransactionSearch search(UUID userId, UUID accountId, DateRange range, String text,
                                    CategoryGroup categoryGroup, String tag, int limit) {
        accountService.requireOwned(userId, accountId);
        String pattern = likePattern(text);
        Collection<CategoryGroup> groups = categoryGroup == null
                ? Arrays.asList(CategoryGroup.values())
                : Set.of(categoryGroup);

        String tagKey = tag == null ? "" : TagService.key(tag);
        boolean anyTag = tagKey.isEmpty();
        List<TypeTotals> totals = transactions.searchTotals(userId, accountId, range.from(), range.to(), pattern, groups,
                anyTag, tagKey);
        TypeTotals expenses = totals.stream().filter(t -> t.type() == TransactionType.EXPENSE).findFirst()
                .orElseGet(() -> empty(TransactionType.EXPENSE));
        TypeTotals income = totals.stream().filter(t -> t.type() == TransactionType.INCOME).findFirst()
                .orElseGet(() -> empty(TransactionType.INCOME));

        List<TransactionLine> latest = transactions.search(userId, accountId, range.from(), range.to(), pattern, groups,
                        anyTag, tagKey, PageRequest.of(0, limit, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
                .stream().map(TransactionLine::from).toList();

        return new TransactionSearch(expenses.count(), expenses.sum(), income.count(), income.sum().negate(), latest);
    }

    /** Expenses per tag in the range, largest first. */
    @Transactional(readOnly = true)
    public List<TagTotal> tagTotals(UUID userId, UUID accountId, DateRange range) {
        accountService.requireOwned(userId, accountId);
        return transactions.tagTotals(userId, accountId, range.from(), range.to(), TransactionType.EXPENSE);
    }

    /** Names of the tags the account's transactions use, most used first. */
    @Transactional(readOnly = true)
    public List<String> tagNames(UUID userId, UUID accountId) {
        accountService.requireOwned(userId, accountId);
        return tagRepository.usageInAccount(userId, accountId).stream().map(TagUsage::name).toList();
    }

    /** "%text%" in lower case, with LIKE wildcards in the text escaped (the queries use escape '\\'). */
    /** A lower-case LIKE pattern containing {@code text}, wildcards escaped; "%" when blank. */
    static String likePattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String escaped = text.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static TypeTotals empty(TransactionType type) {
        return new TypeTotals(type, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
