import type { GoalStatus } from "./api";

export type { GoalStatus };

/** A savings goal with its progress and projection, as computed by the API. */
export interface Goal {
  id: string;
  name: string;
  targetAmount: number;
  /** "YYYY-MM-DD" or null (no deadline). */
  targetDate: string | null;
  saved: number;
  remaining: number;
  /** Can exceed 100. */
  percentage: number;
  /** Money put in per month lately (last 90 days, or since the goal started). */
  monthlyPace: number;
  /** Per month still needed to arrive on time; null without a date or once completed. */
  requiredMonthly: number | null;
  /** When the target is reached at the current pace; null without a positive pace. */
  projectedDate: string | null;
  status: GoalStatus;
}

export interface GoalContribution {
  id: string;
  /** Negative: a withdrawal. */
  amount: number;
  date: string;
  note: string | null;
}

export interface GoalInput {
  name: string;
  targetAmount: number;
  targetDate: string | null;
}
