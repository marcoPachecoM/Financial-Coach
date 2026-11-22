import api from "./instance";
import { toNumber } from "./money";
import type { ApiAnalyticsSummary, ApiCategoryGroupBreakdown, TransactionType } from "../types/api";
import type { CategoryGroupSummaryItem } from "../types/transaction";
import type { TransactionFilters } from "./transactions";

export interface AnalyticsSummary {
  totalExpenses: number;
  totalIncome: number;
  net: number;
  expenseCount: number;
  incomeCount: number;
  averageExpense: number;
  largestExpense: number;
}

const params = ({ accountId, startDate, endDate }: TransactionFilters) => ({
  account_id: accountId,
  start_date: startDate,
  end_date: endDate,
});

export const getAnalyticsSummary = async (filters: TransactionFilters): Promise<AnalyticsSummary> => {
  const res = await api.get<ApiAnalyticsSummary>("/analytics/summary", { params: params(filters) });
  const s = res.data;
  return {
    totalExpenses: toNumber(s.total_expenses),
    totalIncome: toNumber(s.total_income),
    net: toNumber(s.net),
    expenseCount: s.expense_count,
    incomeCount: s.income_count,
    averageExpense: toNumber(s.average_expense),
    largestExpense: toNumber(s.largest_expense),
  };
};

/** Largest group first. Amounts are positive. */
export const getAnalyticsByCategoryGroup = async (
  filters: TransactionFilters,
  type: TransactionType = "EXPENSE",
): Promise<CategoryGroupSummaryItem[]> => {
  const res = await api.get<ApiCategoryGroupBreakdown[]>("/analytics/by-category-group", {
    params: { ...params(filters), type },
  });
  return res.data.map((item) => ({
    categoryGroup: item.category_group,
    amount: toNumber(item.amount),
    percentage: toNumber(item.percentage),
    count: item.count,
  }));
};
