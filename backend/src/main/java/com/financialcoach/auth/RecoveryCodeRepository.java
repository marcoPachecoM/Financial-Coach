package com.financialcoach.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteByUser(@Param("userId") UUID userId);

    /**
     * Marks the code used if it is one of the user's unused codes; returns 1 when it was, 0 otherwise.
     * A single conditional update, so two requests with the same code cannot both succeed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecoveryCode c set c.usedAt = :now
            where c.userId = :userId and c.codeHash = :codeHash and c.usedAt is null
            """)
    int markUsed(@Param("userId") UUID userId, @Param("codeHash") String codeHash, @Param("now") Instant now);

    @Query("select count(c) from RecoveryCode c where c.userId = :userId and c.usedAt is null")
    long countUnused(@Param("userId") UUID userId);
}
