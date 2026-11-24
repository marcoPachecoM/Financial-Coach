package com.financialcoach.goals;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Callers own the goal (checked first), except {@link #findOwned}, which checks it itself. */
interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    /** Newest first; same date: last recorded first. */
    @Query("select c from GoalContribution c where c.goalId = :goalId order by c.contributedOn desc, c.createdAt desc")
    List<GoalContribution> findByGoal(@Param("goalId") UUID goalId);

    /** The contributions of several goals in one query (the list of goals). */
    @Query("select c from GoalContribution c where c.goalId in :goalIds")
    List<GoalContribution> findByGoals(@Param("goalIds") Collection<UUID> goalIds);

    @Query("""
            select c from GoalContribution c, SavingsGoal g
            where c.goalId = g.id and c.id = :id and g.id = :goalId and g.userId = :userId
            """)
    Optional<GoalContribution> findOwned(@Param("id") UUID id, @Param("goalId") UUID goalId,
                                         @Param("userId") UUID userId);
}
