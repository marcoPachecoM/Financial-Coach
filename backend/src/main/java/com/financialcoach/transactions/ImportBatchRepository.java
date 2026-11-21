package com.financialcoach.transactions;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
}
