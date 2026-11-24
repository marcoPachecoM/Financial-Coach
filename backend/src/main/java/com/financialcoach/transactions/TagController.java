package com.financialcoach.transactions;

import com.financialcoach.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /tags}: the user's tags in use, for autocompletion and the filter chips. */
@RestController
class TagController {

    private final TagService tagService;

    TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** Most used first; with account_id, only that account's transactions count. */
    @GetMapping("/api/v1/tags")
    List<TagUsage> list(@AuthenticationPrincipal AuthenticatedUser user,
                        @RequestParam(name = "account_id", required = false) UUID accountId) {
        return tagService.usage(user.id(), accountId);
    }
}
