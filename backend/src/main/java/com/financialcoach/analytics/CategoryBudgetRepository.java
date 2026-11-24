package com.financialcoach.analytics;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Callers check that the account belongs to the user first (AccountService.requireOwned). */
interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, UUID> {

    List<CategoryBudget> findByAccountId(UUID accountId);

    /**
     * Runs at once (a bulk delete), so the new rows of a replacement can be inserted right after
     * without hitting ux_category_budgets_account_group: Hibernate would otherwise run the inserts
     * before the deletes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoryBudget b where b.accountId = :accountId")
    void deleteByAccount(@Param("accountId") UUID accountId);
}
