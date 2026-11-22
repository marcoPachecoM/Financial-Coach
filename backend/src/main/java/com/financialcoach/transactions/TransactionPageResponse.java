package com.financialcoach.transactions;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * {@code TransactionPage} schema of openapi.yaml. Pages are 1-based for clients; Spring Data
 * pages are 0-based, the conversion happens here and in TransactionService.
 * {@code size} is the number of items in this page, not the requested size.
 */
record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    static TransactionPageResponse from(Page<Transaction> page) {
        List<TransactionResponse> content = page.getContent().stream().map(TransactionResponse::from).toList();
        return new TransactionPageResponse(
                content,
                page.getNumber() + 1,
                content.size(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
