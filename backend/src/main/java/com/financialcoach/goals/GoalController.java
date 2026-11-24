package com.financialcoach.goals;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Savings goals endpoints of openapi.yaml. The owner always comes from the authenticated user. */
@RestController
@RequestMapping("/api/v1/goals")
class GoalController {

    private static final Set<String> NO_PARAMETERS = Set.of();

    private final GoalService goalService;

    GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    List<GoalResponse> list(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        QueryParameters.requireOnly(request, NO_PARAMETERS);
        return goalService.list(user.id());
    }

    @PostMapping
    ResponseEntity<GoalResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody GoalCreateRequest body,
                                        UriComponentsBuilder uriBuilder) {
        GoalResponse goal = goalService.create(user.id(), body);
        URI location = uriBuilder.path("/api/v1/goals/{id}").buildAndExpand(goal.id()).toUri();
        return ResponseEntity.created(location).body(goal);
    }

    @GetMapping("/{goalId}")
    GoalResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId) {
        return goalService.get(user.id(), goalId);
    }

    @PatchMapping("/{goalId}")
    GoalResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId,
                        @Valid @RequestBody GoalUpdateRequest body) {
        return goalService.update(user.id(), goalId, body);
    }

    @DeleteMapping("/{goalId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId) {
        goalService.delete(user.id(), goalId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{goalId}/contributions")
    List<GoalContributionResponse> contributions(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID goalId) {
        return goalService.contributions(user.id(), goalId);
    }

    /** Answers with the goal and its new progress (the card and the panel refresh from it). */
    @PostMapping("/{goalId}/contributions")
    ResponseEntity<GoalResponse> contribute(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID goalId,
                                            @Valid @RequestBody GoalContributionRequest body) {
        return ResponseEntity.status(201).body(goalService.contribute(user.id(), goalId, body));
    }

    @DeleteMapping("/{goalId}/contributions/{contributionId}")
    GoalResponse deleteContribution(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId,
                                    @PathVariable UUID contributionId) {
        return goalService.deleteContribution(user.id(), goalId, contributionId);
    }
}
