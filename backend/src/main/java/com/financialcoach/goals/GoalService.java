package com.financialcoach.goals;

import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ConflictException;
import com.financialcoach.shared.error.NotFoundException;
import com.financialcoach.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Savings goals of a user and their contributions. Every method checks ownership (404 otherwise). */
@Service
class GoalService {

    private static final String DUPLICATE_NAME = "A goal with this name already exists.";
    private static final String UNIQUE_NAME_CONSTRAINT = "ux_savings_goals_user_name";
    private static final String NOT_FOUND = "Goal not found.";

    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final Clock clock;

    GoalService(SavingsGoalRepository goals, GoalContributionRepository contributions, Clock clock) {
        this.goals = goals;
        this.contributions = contributions;
        this.clock = clock;
    }

    /** Goals needing attention first (overdue, behind), completed last; ties by creation. */
    @Transactional(readOnly = true)
    List<GoalResponse> list(UUID userId) {
        List<SavingsGoal> owned = goals.findAllOwnedBy(userId);
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<GoalContribution>> byGoal = contributions.findByGoals(owned.stream().map(SavingsGoal::getId).toList())
                .stream().collect(Collectors.groupingBy(GoalContribution::getGoalId));
        return owned.stream()
                .map(goal -> GoalResponse.from(goal, project(goal, byGoal.getOrDefault(goal.getId(), List.of()))))
                .sorted(Comparator.comparing(GoalResponse::status))
                .toList();
    }

    @Transactional(readOnly = true)
    GoalResponse get(UUID userId, UUID goalId) {
        return response(owned(userId, goalId));
    }

    @Transactional
    GoalResponse create(UUID userId, GoalCreateRequest request) {
        String name = request.name().strip();
        if (goals.existsByName(userId, name, null)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        BigDecimal target = positiveTarget(request.targetAmount());
        LocalDate targetDate = notPast(request.targetDate());
        return response(save(new SavingsGoal(userId, name, target, targetDate)));
    }

    @Transactional
    GoalResponse update(UUID userId, UUID goalId, GoalUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new ValidationException("At least one field must be provided.");
        }
        SavingsGoal goal = owned(userId, goalId);
        if (request.nameProvided()) {
            if (request.name() == null) {
                throw new ValidationException("name", "must not be null");
            }
            String name = request.name().strip();
            if (goals.existsByName(userId, name, goalId)) {
                throw new ConflictException(DUPLICATE_NAME);
            }
            goal.rename(name);
        }
        if (request.targetAmountProvided()) {
            if (request.targetAmount() == null) {
                throw new ValidationException("target_amount", "must not be null");
            }
            goal.changeTargetAmount(positiveTarget(request.targetAmount()));
        }
        if (request.targetDateProvided()) {
            goal.changeTargetDate(notPast(request.targetDate()));
        }
        return response(save(goal));
    }

    @Transactional
    void delete(UUID userId, UUID goalId) {
        goals.delete(owned(userId, goalId)); // contributions go with it (ON DELETE CASCADE)
        goals.flush();
    }

    @Transactional(readOnly = true)
    List<GoalContributionResponse> contributions(UUID userId, UUID goalId) {
        owned(userId, goalId);
        return contributions.findByGoal(goalId).stream().map(GoalContributionResponse::from).toList();
    }

    /** Returns the goal with its new progress. A withdrawal cannot take out more than was saved. */
    @Transactional
    GoalResponse contribute(UUID userId, UUID goalId, GoalContributionRequest request) {
        SavingsGoal goal = owned(userId, goalId);
        BigDecimal amount = Money.parse(request.amount());
        if (amount.signum() == 0) {
            throw new ValidationException("amount", "must not be zero");
        }
        LocalDate date = request.date() == null ? today() : request.date();
        if (date.isAfter(today())) {
            throw new ValidationException("date", "must not be in the future");
        }
        if (saved(goalId).add(amount).signum() < 0) {
            throw new ValidationException("amount", "cannot withdraw more than what was saved");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        contributions.saveAndFlush(new GoalContribution(goalId, amount, date, note));
        return response(goal);
    }

    @Transactional
    GoalResponse deleteContribution(UUID userId, UUID goalId, UUID contributionId) {
        SavingsGoal goal = owned(userId, goalId);
        GoalContribution contribution = contributions.findOwned(contributionId, goalId, userId)
                .orElseThrow(() -> new NotFoundException("Contribution not found."));
        if (saved(goalId).subtract(contribution.getAmount()).signum() < 0) {
            throw new ValidationException("Deleting this contribution would leave the goal below zero;"
                    + " delete the withdrawals after it first.");
        }
        contributions.delete(contribution);
        contributions.flush();
        return response(goal);
    }

    private SavingsGoal owned(UUID userId, UUID goalId) {
        return goals.findOwnedBy(goalId, userId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private GoalResponse response(SavingsGoal goal) {
        return GoalResponse.from(goal, project(goal, contributions.findByGoal(goal.getId())));
    }

    private GoalProjection project(SavingsGoal goal, List<GoalContribution> entries) {
        LocalDate createdOn = goal.getCreatedAt() == null
                ? today()
                : goal.getCreatedAt().atZone(zone()).toLocalDate();
        return GoalProjection.of(goal.getTargetAmount(), goal.getTargetDate(), createdOn,
                entries.stream().map(GoalContribution::toEntry).toList(), today());
    }

    private BigDecimal saved(UUID goalId) {
        return contributions.findByGoal(goalId).stream().map(GoalContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal positiveTarget(String value) {
        BigDecimal target = Money.parse(value);
        if (target.signum() <= 0) {
            throw new ValidationException("target_amount", "must be greater than 0");
        }
        return target;
    }

    private LocalDate notPast(LocalDate date) {
        if (date != null && date.isBefore(today())) {
            throw new ValidationException("target_date", "must not be in the past");
        }
        return date;
    }

    /** The unique index is the real guarantee against two requests racing; it becomes a 409 too. */
    private SavingsGoal save(SavingsGoal goal) {
        try {
            return goals.saveAndFlush(goal);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(UNIQUE_NAME_CONSTRAINT)) {
                throw new ConflictException(DUPLICATE_NAME);
            }
            throw e;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ZoneId zone() {
        return clock.getZone();
    }
}
