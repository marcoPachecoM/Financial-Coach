import api from "./instance";
import { toNumber, toNumberOrNull } from "./money";
import type { ApiCategoryBudgets } from "../types/api";
import type { CategoryBudgets } from "../types/budget";

const toCategoryBudgets = (data: ApiCategoryBudgets): CategoryBudgets => ({
  month: data.month,
  budgets: data.budgets.map((b) => ({
    categoryGroup: b.category_group,
    budget: toNumber(b.budget),
    spent: toNumber(b.spent),
    remaining: toNumber(b.remaining),
    percentageUsed: toNumberOrNull(b.percentage_used),
    status: b.status,
  })),
  unbudgeted: data.unbudgeted.map((u) => ({ categoryGroup: u.category_group, spent: toNumber(u.spent), count: u.count })),
});

/** The account's category budgets with the month's spending (current month by default). */
export const getCategoryBudgets = async (accountId: string, month?: string): Promise<CategoryBudgets> => {
  const res = await api.get<ApiCategoryBudgets>(`/accounts/${accountId}/category-budgets`, {
    params: month ? { month } : undefined,
  });
  return toCategoryBudgets(res.data);
};

/** Replaces every category budget of the account (groups left out lose theirs). */
export const saveCategoryBudgets = async (
  accountId: string,
  budgets: { categoryGroup: string; monthlyAmount: number }[],
): Promise<CategoryBudgets> => {
  const res = await api.put<ApiCategoryBudgets>(`/accounts/${accountId}/category-budgets`, {
    budgets: budgets.map((b) => ({ category_group: b.categoryGroup, monthly_amount: b.monthlyAmount.toFixed(2) })),
  });
  return toCategoryBudgets(res.data);
};
