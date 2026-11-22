package com.financialcoach.categorization;

import org.springframework.data.jpa.repository.JpaRepository;

interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, String> {
}
