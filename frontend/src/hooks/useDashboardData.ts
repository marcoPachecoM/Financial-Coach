import { getAccounts, getBudgetUsage } from "../services/accounts";
import { getAlerts } from "../services/alerts";
import { getRecurringTransactions } from "../services/recurring";
import { getAnalyticsByCategoryGroup, getAnalyticsSummary } from "../services/analytics";
import { getAllTransactions, type TransactionFilters } from "../services/transactions";
import { getCurrentUser } from "../services/users";
import { useAsync } from "./useAsync";
import type { BudgetUsage } from "../types/budget";

/** Stable cache key for a set of filters (null until an account is selected). */
const filtersKey = (filters: TransactionFilters | null) =>
  filters ? `${filters.accountId}|${filters.startDate ?? ""}|${filters.endDate ?? ""}` : null;

export const useAccounts = () => useAsync(getAccounts, "accounts");

export const useCurrentUser = () => useAsync(getCurrentUser, "me");

export const useTransactions = (filters: TransactionFilters | null) =>
  useAsync(() => getAllTransactions(filters!), filtersKey(filters));

export const useAnalyticsSummary = (filters: TransactionFilters | null) =>
  useAsync(() => getAnalyticsSummary(filters!), filtersKey(filters));

export const useCategoryGroupSummary = (filters: TransactionFilters | null) =>
  useAsync(() => getAnalyticsByCategoryGroup(filters!), filtersKey(filters));

/** Current month's budget usage of the account. */
export const useBudgetUsage = (accountId: string | null) =>
  useAsync(() => getBudgetUsage(accountId!), accountId);

/** Recurring transactions of the account. */
export const useRecurringTransactions = (accountId: string | null) =>
  useAsync(() => getRecurringTransactions(accountId!), accountId);

/** Current month's alerts of the account, most severe first. */
export const useAlerts = (accountId: string | null) => useAsync(() => getAlerts(accountId!), accountId);

/** "YYYY-MM" of the month before the current one (local time). */
const previousMonth = () => {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

export interface AccountSpending {
  current: BudgetUsage;
  /** Spent in the previous month. */
  previousSpent: number;
}

/** For the sidebar: this month's budget usage and last month's spending of every account. */
export const useAccountsSpending = (accountIds: string[]) =>
  useAsync(async () => {
    const month = previousMonth();
    const entries = await Promise.all(
      accountIds.map(async (id) => {
        const [current, previous] = await Promise.all([getBudgetUsage(id), getBudgetUsage(id, month)]);
        return [id, { current, previousSpent: previous.spent }] as const;
      }),
    );
    return Object.fromEntries(entries) as Record<string, AccountSpending>;
  }, accountIds.length ? `spending|${accountIds.join(",")}` : null);
