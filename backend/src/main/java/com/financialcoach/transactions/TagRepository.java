package com.financialcoach.transactions;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query filters by owner. */
interface TagRepository extends JpaRepository<Tag, UUID> {

    /** The user's tags with these lower-case names. */
    @Query("select g from Tag g where g.userId = :userId and lower(g.name) in :keys")
    List<Tag> findByKeys(@Param("userId") UUID userId, @Param("keys") Collection<String> keys);

    /**
     * Creates the tag unless the user already has one with that name. A concurrent request
     * creating the same tag makes this wait for it and then do nothing, instead of failing on
     * ux_tags_user_name; the caller reads the tag afterwards.
     */
    @Modifying
    @Query(value = """
            INSERT INTO tags (user_id, name) VALUES (:userId, :name)
            ON CONFLICT (user_id, lower(name)) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") UUID userId, @Param("name") String name);

    /**
     * Tags in use, most used first: soft-deleted transactions do not count (the entity's
     * restriction applies), so a tag nobody uses any more disappears from the list.
     */
    @Query("""
            select new com.financialcoach.transactions.TagUsage(g.name, count(t))
            from Transaction t join t.tags g
            where t.userId = :userId
            group by g.name
            order by count(t) desc, lower(g.name)
            """)
    List<TagUsage> usage(@Param("userId") UUID userId);

    /** Same, counting only the transactions of one account. */
    @Query("""
            select new com.financialcoach.transactions.TagUsage(g.name, count(t))
            from Transaction t join t.tags g
            where t.userId = :userId and t.accountId = :accountId
            group by g.name
            order by count(t) desc, lower(g.name)
            """)
    List<TagUsage> usageInAccount(@Param("userId") UUID userId, @Param("accountId") UUID accountId);
}
