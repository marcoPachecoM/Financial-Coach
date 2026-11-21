import { getAccounts, getBudgetUsage } from "../services/accounts";
import { getAlerts } from "../services/alerts";
import { getRecurringTransactions } from "../services/recurring";
import { getAnalyticsByCategoryGroup, getAnalyticsSummary } from "../services/analytics";
import { getAllTransactions, type TransactionFilters } from "../services/transactions";
import { getCurrentUser } from "../services/users";
import { useAsync } from "./useAsync";

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
