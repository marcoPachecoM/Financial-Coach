import type { BudgetStatus } from "./api";

export interface BudgetUsage {
  month: string;
  budget: number | null;
  spent: number;
  remaining: number | null;
  percentageUsed: number | null;
  status: BudgetStatus;
}
