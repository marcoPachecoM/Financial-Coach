package com.financialcoach.transactions;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query a request can reach filters by owner; the generator works across users. */
interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    @Query("select r from RecurringTransaction r where r.id = :id and r.userId = :userId")
    Optional<RecurringTransaction> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select r from RecurringTransaction r
            where r.userId = :userId and r.accountId = :accountId
            order by lower(r.name), r.id
            """)
    List<RecurringTransaction> findByAccount(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    @Query("select r from RecurringTransaction r where r.userId = :userId and r.accountId in :accountIds")
    List<RecurringTransaction> findByAccounts(@Param("userId") UUID userId,
                                              @Param("accountIds") Collection<UUID> accountIds);

    /** Rules with an occurrence due on or before {@code today} (ix_recurring_transactions_next_due_date). */
    @Query("select r.id from RecurringTransaction r where r.nextDueDate <= :today order by r.nextDueDate, r.id")
    List<UUID> findDueIds(@Param("today") LocalDate today);

    /**
     * Locks the rule if it is still due. SKIP LOCKED: when two instances run the generator at the
     * same time, each rule is processed by only one of them (the other gets an empty result).
     */
    @Query(value = """
            SELECT * FROM recurring_transactions
            WHERE id = :id AND next_due_date <= :today
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<RecurringTransaction> lockIfDue(@Param("id") UUID id, @Param("today") LocalDate today);
}
