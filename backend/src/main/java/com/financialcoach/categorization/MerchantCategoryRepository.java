package com.financialcoach.categorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, String> {

    /**
     * Caches a classification unless the merchant already has one. Two imports classifying the
     * same merchant at once both succeed instead of one failing on the primary key.
     */
    @Modifying
    @Query(value = """
            INSERT INTO merchant_category_cache (merchant_name, category_group, source)
            VALUES (:name, :group, :source)
            ON CONFLICT (merchant_name) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("group") String group, @Param("source") String source);
}
