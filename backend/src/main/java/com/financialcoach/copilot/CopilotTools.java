package com.financialcoach.copilot;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.financialcoach.shared.DateRange;
import com.financialcoach.transactions.TransactionStatistics;
import com.financialcoach.transactions.TransactionTotals;

public class CopilotTools {
    private final TransactionStatistics statistics;
    private final UUID userId;
    private final UUID accountId;

    CopilotTools(TransactionStatistics statistics, UUID userId, UUID accountId) {
        this.statistics = statistics;
        this.userId = userId;
        this.accountId = accountId;
    }

    @Tool (description = "Total expenses and income of the user's account between two dates (inclusive).")
    String summary(@ToolParam(description = "Start date, yyyy-MM-dd") LocalDate startDate,
                   @ToolParam(description = "End date, yyyy-MM-dd") LocalDate endDate) {
        TransactionTotals totals = statistics.totals(userId, accountId, DateRange.between(startDate, endDate));
        return "Expenses: %s (%d transactions). Income: %s.".formatted(
                totals.expenseTotal(), totals.expenseCount(), totals.incomeTotal());
    }
}
