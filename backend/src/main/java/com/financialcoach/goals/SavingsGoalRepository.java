package com.financialcoach.goals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query filters by owner. */
interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    @Query("select g from SavingsGoal g where g.userId = :userId order by g.createdAt")
    List<SavingsGoal> findAllOwnedBy(@Param("userId") UUID userId);

    @Query("select g from SavingsGoal g where g.id = :id and g.userId = :userId")
    Optional<SavingsGoal> findOwnedBy(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select count(g) > 0 from SavingsGoal g
            where g.userId = :userId and lower(g.name) = lower(:name) and (:excluded is null or g.id <> :excluded)
            """)
    boolean existsByName(@Param("userId") UUID userId, @Param("name") String name, @Param("excluded") UUID excluded);
}
