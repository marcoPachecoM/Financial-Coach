package com.financialcoach.transactions;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Amounts as positive values; largest first, ties by category name. */
    @Query("""
            select new com.financialcoach.transactions.CategoryTotal(t.category, count(t), abs(sum(t.amount)))
            from Transaction t
            where t.userId = :userId and t.accountId = :accountId and t.type = :type
              and t.occurredAt >= :from and t.occurredAt < :to
            group by t.category
            order by abs(sum(t.amount)) desc, t.category
            """)
    List<CategoryTotal> totalsByCategory(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                         @Param("type") TransactionType type,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
