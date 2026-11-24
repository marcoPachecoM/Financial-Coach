import api from "./instance";
import { toNumber, toNumberOrNull } from "./money";
import type { ApiGoal, ApiGoalContribution } from "../types/api";
import type { Goal, GoalContribution, GoalInput } from "../types/goal";

const toGoal = (g: ApiGoal): Goal => ({
  id: g.id,
  name: g.name,
  targetAmount: toNumber(g.target_amount),
  targetDate: g.target_date,
  saved: toNumber(g.saved),
  remaining: toNumber(g.remaining),
  percentage: toNumber(g.percentage),
  monthlyPace: toNumber(g.monthly_pace),
  requiredMonthly: toNumberOrNull(g.required_monthly),
  projectedDate: g.projected_date,
  status: g.status,
});

const body = (input: GoalInput) => ({
  name: input.name,
  target_amount: input.targetAmount.toFixed(2),
  target_date: input.targetDate,
});

/** The user's goals (not tied to an account), the ones needing attention first. */
export const getGoals = async (): Promise<Goal[]> => {
  const res = await api.get<ApiGoal[]>("/goals");
  return res.data.map(toGoal);
};

export const createGoal = async (input: GoalInput): Promise<Goal> => {
  const res = await api.post<ApiGoal>("/goals", body(input));
  return toGoal(res.data);
};

/** Sends every field: a null target date removes it. */
export const updateGoal = async (id: string, input: GoalInput): Promise<Goal> => {
  const res = await api.patch<ApiGoal>(`/goals/${id}`, body(input));
  return toGoal(res.data);
};

export const deleteGoal = async (id: string): Promise<void> => {
  await api.delete(`/goals/${id}`);
};

/** Newest first. */
export const getContributions = async (goalId: string): Promise<GoalContribution[]> => {
  const res = await api.get<ApiGoalContribution[]>(`/goals/${goalId}/contributions`);
  return res.data.map((c) => ({ id: c.id, amount: toNumber(c.amount), date: c.date, note: c.note }));
};

/** A negative amount is a withdrawal. Returns the goal with its new progress. */
export const addContribution = async (
  goalId: string,
  contribution: { amount: number; date: string; note: string | null },
): Promise<Goal> => {
  const res = await api.post<ApiGoal>(`/goals/${goalId}/contributions`, {
    amount: contribution.amount.toFixed(2),
    date: contribution.date,
    note: contribution.note,
  });
  return toGoal(res.data);
};

export const deleteContribution = async (goalId: string, contributionId: string): Promise<Goal> => {
  const res = await api.delete<ApiGoal>(`/goals/${goalId}/contributions/${contributionId}`);
  return toGoal(res.data);
};
