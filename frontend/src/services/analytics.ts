import api from "./instance";
import { toNumber } from "./money";
import type { ApiAnalyticsSummary, ApiCategoryGroupBreakdown, ApiDailyTotal, TransactionType } from "../types/api";
import type { CategoryGroupSummaryItem, DailyTotal } from "../types/transaction";
import type { TransactionFilters } from "./transactions";

export interface AnalyticsSummary {
  totalExpenses: number;
  totalIncome: number;
  net: number;
  expenseCount: number;
  incomeCount: number;
  averageExpense: number;
  largestExpense: number;
  /** First and last transaction of the period ("YYYY-MM-DD"); null when there are none. */
  firstDate: string | null;
  lastDate: string | null;
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
    firstDate: s.first_date,
    lastDate: s.last_date,
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

/**
 * One entry per day with transactions, oldest first: the calendar and the spending trend. The
 * server adds them up, so the browser never downloads the transactions themselves.
 */
export const getDailyTotals = async (
  filters: TransactionFilters,
  categoryGroup: string | null = null,
): Promise<DailyTotal[]> => {
  const res = await api.get<ApiDailyTotal[]>("/analytics/daily", {
    params: { ...params(filters), category_group: categoryGroup ?? undefined },
  });
  return res.data.map((day) => ({
    date: day.date,
    expenseTotal: toNumber(day.expense_total),
    expenseCount: day.expense_count,
    transactionCount: day.transaction_count,
  }));
};

/** Category groups with transactions of any type in the period, most transactions first. */
export const getCategoryGroupsInPeriod = async (filters: TransactionFilters): Promise<string[]> => {
  const [expenses, income] = await Promise.all([
    getAnalyticsByCategoryGroup(filters, "EXPENSE"),
    getAnalyticsByCategoryGroup(filters, "INCOME"),
  ]);
  const counts = new Map<string, number>();
  [...expenses, ...income].forEach((g) => counts.set(g.categoryGroup, (counts.get(g.categoryGroup) ?? 0) + g.count));
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([group]) => group);
};
