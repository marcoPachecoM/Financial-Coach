import type { BudgetStatus } from "./api";

export interface BudgetUsage {
  month: string;
  budget: number | null;
  spent: number;
  remaining: number | null;
  percentageUsed: number | null;
  status: BudgetStatus;
}

/** One category group with a budget, in the current month. */
export interface CategoryBudgetUsage {
  categoryGroup: string;
  budget: number;
  spent: number;
  /** Negative when over budget. */
  remaining: number;
  /** Null for a zero budget. */
  percentageUsed: number | null;
  status: BudgetStatus;
}

/** Budgets per category group of an account, most used first, and the spending without one. */
export interface CategoryBudgets {
  month: string;
  budgets: CategoryBudgetUsage[];
  /** Groups with spending this month and no budget, largest first. */
  unbudgeted: { categoryGroup: string; spent: number; count: number }[];
}
