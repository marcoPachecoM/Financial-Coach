package com.financialcoach.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query filters by owner: a user can never load another user's account by id.
 * Name comparisons use lower() to match the ux_accounts_user_name index.
 */
interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("select a from Account a where a.userId = :userId order by lower(a.name)")
    List<Account> findAllOwnedBy(@Param("userId") UUID userId);

    @Query("select a from Account a where a.id = :id and a.userId = :userId")
    Optional<Account> findOwnedBy(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select count(a) > 0 from Account a
            where a.userId = :userId and lower(a.name) = lower(:name)
            """)
    boolean existsByName(@Param("userId") UUID userId, @Param("name") String name);

    @Query("""
            select count(a) > 0 from Account a
            where a.userId = :userId and lower(a.name) = lower(:name) and a.id <> :excludedId
            """)
    boolean existsByNameExcluding(@Param("userId") UUID userId, @Param("name") String name,
                                  @Param("excludedId") UUID excludedId);
}
