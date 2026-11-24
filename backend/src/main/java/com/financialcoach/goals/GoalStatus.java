package com.financialcoach.goals;

/**
 * {@code GoalStatus} of openapi.yaml. Declared in the order the list shows the goals: the ones
 * that need attention first.
 */
enum GoalStatus {
    /** The target date passed and the goal was not reached. */
    OVERDUE,
    /** At the current pace (or with no pace yet) the goal is not reached by its target date. */
    BEHIND,
    /** At the current pace the goal is reached on or before its target date. */
    ON_TRACK,
    /** No target date: only the projection applies. */
    NO_TARGET_DATE,
    /** Saved at least the target. */
    COMPLETED
}
