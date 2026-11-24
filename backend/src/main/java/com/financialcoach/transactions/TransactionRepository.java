package com.financialcoach.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.financialcoach.categorization.CategoryGroup;

/**
 * Every query filters by owner. Soft-deleted rows are excluded by the entity's @SQLRestriction.
 */
interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * The transaction list: {@code from} inclusive, {@code to} exclusive. {@code pattern} is a
     * lower-case LIKE pattern matched against name, category, notes and tag names ("%" matches
     * everything), {@code groups} the allowed category groups, the absolute amount must be within
     * {@code minAmount}..{@code maxAmount} (inclusive), and unless {@code anyTag}, the transaction
     * must have the tag whose lower-case name is {@code tagKey}. Order comes from the Pageable.
     */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
              and t.categoryGroup in :groups
              and (lower(t.name) like :pattern escape '\\'
                   or lower(coalesce(t.category, '')) like :pattern escape '\\'
                   or lower(coalesce(t.notes, '')) like :pattern escape '\\'
                   or exists (select 1 from t.tags g where lower(g.name) like :pattern escape '\\'))
              and abs(t.amount) >= :minAmount and abs(t.amount) <= :maxAmount
              and (:anyTag = true or exists (select 1 from t.tags g where lower(g.name) = :tagKey))
            """)
    Page<Transaction> findPage(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("pattern") String pattern, @Param("groups") Collection<CategoryGroup> groups,
                               @Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount,
                               @Param("anyTag") boolean anyTag, @Param("tagKey") String tagKey,
                               Pageable pageable);

    @Query("select t from Transaction t where t.id = :id and t.userId = :userId")
    Optional<Transaction> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Fingerprints that already exist in these accounts. Native SQL on purpose: it must include
     * soft-deleted rows (which @SQLRestriction would hide), so re-importing a file does not
     * bring back transactions the user deleted. Uses the ux_transactions_account_fingerprint index.
     */
    @Query(value = """
            SELECT fingerprint FROM transactions
            WHERE account_id IN (:accountIds) AND fingerprint IN (:fingerprints)
            """, nativeQuery = true)
    List<String> findExistingFingerprints(@Param("accountIds") Collection<UUID> accountIds,
                                          @Param("fingerprints") Collection<String> fingerprints);

    /**
     * Active transactions without a CSV fingerprint (typed in by hand or created by a recurring
     * rule) at any of these times. The import compares them with its rows to skip duplicates, e.g.
     * when a file exported by this app is imported again.
     */
    @Query("""
            select new com.financialcoach.transactions.TransactionIdentity(t.accountId, t.occurredAt, t.name, t.amount)
            from Transaction t
            where t.userId = :userId and t.accountId in :accountIds
              and t.fingerprint is null and t.occurredAt in :times
            """)
    List<TransactionIdentity> findWithoutFingerprintAt(@Param("userId") UUID userId,
                                                       @Param("accountIds") Collection<UUID> accountIds,
                                                       @Param("times") Collection<LocalDateTime> times);

    /** The active transaction covering this occurrence of a rule (ux_transactions_recurrence). */
    @Query("""
            select t from Transaction t
            where t.recurringTransactionId = :ruleId and t.recurrenceDate = :dueDate
            """)
    Optional<Transaction> findOccurrence(@Param("ruleId") UUID ruleId, @Param("dueDate") LocalDate dueDate);

    /** The latest active occurrence of a rule: new occurrences copy its tags. */
    Optional<Transaction> findFirstByRecurringTransactionIdOrderByRecurrenceDateDesc(UUID recurringTransactionId);

    /** Active transactions of these rules whose occurrence is due in [from, to]. */
    @Query("""
            select t from Transaction t
            where t.recurringTransactionId in :ruleIds
              and t.recurrenceDate >= :from and t.recurrenceDate <= :to
            """)
    List<Transaction> findOccurrences(@Param("ruleIds") Collection<UUID> ruleIds,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Active transactions of a rule in the period, by transaction date ({@code to} exclusive). */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.accountId = :accountId and t.recurringTransactionId is not null
              and t.occurredAt >= :from and t.occurredAt < :to
            order by t.occurredAt, t.id
            """)
    List<Transaction> findLinked(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Active transactions not linked to any rule, oldest first ({@code to} exclusive). */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.accountId = :accountId and t.type = :type
              and t.recurringTransactionId is null
              and t.occurredAt >= :from and t.occurredAt < :to
            order by t.occurredAt, t.id
            """)
    List<Transaction> findUnlinked(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                   @Param("type") TransactionType type,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Date of the latest imported (CSV) transaction of the account: how far the bank statements go. */
    @Query("""
            select max(t.occurredAt) from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.source = com.financialcoach.transactions.TransactionSource.CSV
            """)
    Optional<LocalDateTime> findLatestImported(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    /**
     * Removes the link to a rule from all its transactions, deleted ones included (they still
     * reference it through the foreign key). Native SQL so @SQLRestriction does not hide them.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE transactions SET recurring_transaction_id = NULL, recurrence_date = NULL
            WHERE recurring_transaction_id = :ruleId
            """, nativeQuery = true)
    int unlinkAll(@Param("ruleId") UUID ruleId);

    /** One row per type that has transactions in the period. {@code largest} is by absolute value. */
    @Query("""
            select new com.financialcoach.transactions.TypeTotals(t.type, count(t), sum(t.amount), max(abs(t.amount)))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
            group by t.type
            """)
    List<TypeTotals> totalsByType(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * One page of the CSV export, oldest first. A constructor projection: the rows are not managed
     * entities, so a large export does not fill the persistence context.
     */
    @Query("""
            select new com.financialcoach.transactions.CsvExportRow(t.id, t.occurredAt, t.type, t.name, t.amount,
                       t.categoryGroup, t.category, t.accountId, t.notes)
            from Transaction t
            where t.userId = :userId and t.accountId in :accountIds
              and t.occurredAt >= :from and t.occurredAt < :to
            order by t.occurredAt, t.id
            """)
    Slice<CsvExportRow> exportPage(@Param("userId") UUID userId, @Param("accountIds") Collection<UUID> accountIds,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   Pageable pageable);

    /** Tag names of these transactions, as [transaction id, tag name] pairs (one page of the export). */
    @Query("select t.id, g.name from Transaction t join t.tags g where t.id in :ids")
    List<Object[]> tagNamesOf(@Param("ids") Collection<UUID> ids);

    /** Rows of the CSV export, known before writing it (sent as a header). */
    @Query("""
            select count(t) from Transaction t
            where t.userId = :userId and t.accountId in :accountIds
              and t.occurredAt >= :from and t.occurredAt < :to
            """)
    long countForExport(@Param("userId") UUID userId, @Param("accountIds") Collection<UUID> accountIds,
                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Sum of the expenses linked to a recurring transaction (EXPENSE amounts are positive). */
    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.type = com.financialcoach.transactions.TransactionType.EXPENSE
              and t.recurringTransactionId is not null
              and t.occurredAt >= :from and t.occurredAt < :to
            """)
    BigDecimal recurringExpenseTotal(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                     @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * One row per day with transactions in the period, oldest first, counting only {@code groups}.
     * Expenses are stored positive, so their sum is already positive.
     */
    @Query("""
            select new com.financialcoach.transactions.DailyTotal(cast(t.occurredAt as LocalDate),
                       sum(case when t.type = :expense then 1L else 0L end),
                       coalesce(sum(case when t.type = :expense then t.amount end), 0),
                       count(t))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
              and t.categoryGroup in :groups
            group by cast(t.occurredAt as LocalDate)
            order by cast(t.occurredAt as LocalDate)
            """)
    List<DailyTotal> dailyTotals(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("groups") Collection<CategoryGroup> groups,
                                 @Param("expense") TransactionType expense);

    @Query("""
            select new com.financialcoach.transactions.DateSpan(min(t.occurredAt), max(t.occurredAt))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
            """)
    DateSpan dateSpan(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Amounts as positive values; largest first, ties by group name. */
    @Query("""
            select new com.financialcoach.transactions.CategoryGroupTotal(t.categoryGroup, count(t), abs(sum(t.amount)))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId and t.type = :type
              and t.occurredAt >= :from and t.occurredAt < :to
            group by t.categoryGroup
            order by abs(sum(t.amount)) desc, t.categoryGroup
            """)
    List<CategoryGroupTotal> totalsByCategoryGroup(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                         @Param("type") TransactionType type,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Search filters of the copilot: {@code pattern} is a lower-case LIKE pattern matched against the
     * name and the category ("%" matches everything); {@code groups} are the category groups allowed.
     */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
              and t.categoryGroup in :groups
              and (lower(t.name) like :pattern escape '\\' or lower(coalesce(t.category, '')) like :pattern escape '\\')
            """)
    List<Transaction> search(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                             @Param("pattern") String pattern, @Param("groups") Collection<CategoryGroup> groups,
                             Pageable pageable);

    /** Totals per type over the same filters as {@link #search}. */
    @Query("""
            select new com.financialcoach.transactions.TypeTotals(t.type, count(t), sum(t.amount), max(abs(t.amount)))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
              and t.categoryGroup in :groups
              and (lower(t.name) like :pattern escape '\\' or lower(coalesce(t.category, '')) like :pattern escape '\\')
            group by t.type
            """)
    List<TypeTotals> searchTotals(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                  @Param("pattern") String pattern, @Param("groups") Collection<CategoryGroup> groups);
}
