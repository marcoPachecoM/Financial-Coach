package com.financialcoach.analytics;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.web.QueryParameters;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /accounts/{account_id}/alerts} of openapi.yaml. */
@RestController
class AlertController {

    private static final Set<String> PARAMETERS = Set.of("month");

    private final AlertService alertService;
    private final Clock clock;

    AlertController(AlertService alertService, Clock clock) {
        this.alertService = alertService;
        this.clock = clock;
    }

    @GetMapping("/api/v1/accounts/{accountId}/alerts")
    List<AlertResponse> alerts(@AuthenticationPrincipal AuthenticatedUser user,
                               HttpServletRequest request,
                               @PathVariable UUID accountId,
                               @RequestParam(name = "month", required = false)
                               @DateTimeFormat(pattern = "uuuu-MM") YearMonth month) {
        QueryParameters.requireOnly(request, PARAMETERS);
        return alertService.alerts(user.id(), accountId, month != null ? month : YearMonth.now(clock));
    }
}
