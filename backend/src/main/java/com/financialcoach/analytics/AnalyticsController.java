package com.financialcoach.analytics;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.web.QueryParameters;
import com.financialcoach.transactions.TransactionType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analytics endpoints of openapi.yaml. */
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {

    private static final Set<String> SUMMARY_PARAMETERS = Set.of("account_id", "start_date", "end_date");
    private static final Set<String> BY_CATEGORY_PARAMETERS = Set.of("account_id", "type", "start_date", "end_date");

    private final AnalyticsService analyticsService;

    AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    AnalyticsSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user,
                                     HttpServletRequest request,
                                     @RequestParam("account_id") UUID accountId,
                                     @RequestParam(name = "start_date", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                     @RequestParam(name = "end_date", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryParameters.requireOnly(request, SUMMARY_PARAMETERS);
        return analyticsService.summary(user.id(), accountId, DateRange.between(startDate, endDate));
    }

    @GetMapping("/by-category")
    List<CategoryBreakdownResponse> byCategory(@AuthenticationPrincipal AuthenticatedUser user,
                                               HttpServletRequest request,
                                               @RequestParam("account_id") UUID accountId,
                                               @RequestParam(name = "type", defaultValue = "EXPENSE")
                                               TransactionType type,
                                               @RequestParam(name = "start_date", required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(name = "end_date", required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryParameters.requireOnly(request, BY_CATEGORY_PARAMETERS);
        return analyticsService.byCategory(user.id(), accountId, type, DateRange.between(startDate, endDate));
    }
}
