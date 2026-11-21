package com.financialcoach.transactions;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.web.QueryParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Recurring transactions endpoints of openapi.yaml. */
@RestController
@RequestMapping("/api/v1/recurring-transactions")
class RecurringTransactionController {

    private static final Set<String> LIST_PARAMETERS = Set.of("account_id");

    private final RecurringTransactionService service;

    RecurringTransactionController(RecurringTransactionService service) {
        this.service = service;
    }

    @GetMapping
    List<RecurringTransactionResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                            HttpServletRequest request,
                                            @RequestParam("account_id") UUID accountId) {
        QueryParameters.requireOnly(request, LIST_PARAMETERS);
        return service.list(user.id(), accountId).stream().map(RecurringTransactionResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<RecurringTransactionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @RequestBody RecurringTransactionCreateRequest request,
                                                        UriComponentsBuilder uriBuilder) {
        RecurringTransaction rule = service.create(user.id(), request);
        URI location = uriBuilder.path("/api/v1/recurring-transactions/{id}").buildAndExpand(rule.getId()).toUri();
        return ResponseEntity.created(location).body(RecurringTransactionResponse.from(rule));
    }

    @GetMapping("/{recurringTransactionId}")
    RecurringTransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable UUID recurringTransactionId) {
        return RecurringTransactionResponse.from(service.get(user.id(), recurringTransactionId));
    }

    @PatchMapping("/{recurringTransactionId}")
    RecurringTransactionResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID recurringTransactionId,
                                        @Valid @RequestBody RecurringTransactionUpdateRequest request) {
        return RecurringTransactionResponse.from(service.update(user.id(), recurringTransactionId, request));
    }

    @DeleteMapping("/{recurringTransactionId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable UUID recurringTransactionId) {
        service.delete(user.id(), recurringTransactionId);
        return ResponseEntity.noContent().build();
    }
}
