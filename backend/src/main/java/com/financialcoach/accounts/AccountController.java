package com.financialcoach.accounts;

import com.financialcoach.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Accounts endpoints of openapi.yaml. The owner always comes from the authenticated user, never from the request. */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.list(user.id()).stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<AccountResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody AccountCreateRequest request,
                                           UriComponentsBuilder uriBuilder) {
        Account account = accountService.create(user.id(), request);
        URI location = uriBuilder.path("/api/v1/accounts/{id}").buildAndExpand(account.getId()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID accountId) {
        return AccountResponse.from(accountService.get(user.id(), accountId));
    }

    @PatchMapping("/{accountId}")
    AccountResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                           @PathVariable UUID accountId,
                           @Valid @RequestBody AccountUpdateRequest request) {
        return AccountResponse.from(accountService.update(user.id(), accountId, request));
    }
}
