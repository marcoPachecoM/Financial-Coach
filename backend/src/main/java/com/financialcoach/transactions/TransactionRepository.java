package com.financialcoach.transactions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query filters by owner. Soft-deleted rows are excluded by the entity's @SQLRestriction.
 */
interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** {@code from} inclusive, {@code to} exclusive. Order comes from the Pageable. */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.accountId = :accountId
              and t.occurredAt >= :from and t.occurredAt < :to
            """)
    Page<Transaction> findPage(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
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

    /** The active transaction covering this occurrence of a rule (ux_transactions_recurrence). */
    @Query("""
            select t from Transaction t
            where t.recurringTransactionId = :ruleId and t.recurrenceDate = :dueDate
            """)
    Optional<Transaction> findOccurrence(@Param("ruleId") UUID ruleId, @Param("dueDate") LocalDate dueDate);

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
}
