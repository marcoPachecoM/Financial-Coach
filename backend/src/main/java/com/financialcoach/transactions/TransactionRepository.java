package com.financialcoach.transactions;

import java.time.LocalDateTime;
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
}
