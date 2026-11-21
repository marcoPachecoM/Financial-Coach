import api from "./instance";
import { toNumber, toNumberOrNull } from "./money";
import type { Account } from "../types/account";
import type { ApiAccount, ApiBudgetUsage } from "../types/api";
import type { BudgetUsage } from "../types/budget";

export const toAccount = (account: ApiAccount): Account => ({
  id: account.id,
  name: account.name,
  monthlyBudget: toNumberOrNull(account.monthly_budget),
});

export const getAccounts = async (): Promise<Account[]> => {
  const res = await api.get<ApiAccount[]>("/accounts");
  return res.data.map(toAccount);
};

const budgetValue = (monthlyBudget: number | null) => (monthlyBudget === null ? null : monthlyBudget.toFixed(2));

export const createAccount = async (name: string, monthlyBudget: number | null): Promise<Account> => {
  const res = await api.post<ApiAccount>("/accounts", { name, monthly_budget: budgetValue(monthlyBudget) });
  return toAccount(res.data);
};

/** Only the fields given are changed; monthlyBudget null removes the budget. */
export const updateAccount = async (
  accountId: string,
  changes: { name?: string; monthlyBudget?: number | null },
): Promise<Account> => {
  const res = await api.patch<ApiAccount>(`/accounts/${accountId}`, {
    ...(changes.name !== undefined ? { name: changes.name } : {}),
    ...(changes.monthlyBudget !== undefined ? { monthly_budget: budgetValue(changes.monthlyBudget) } : {}),
  });
  return toAccount(res.data);
};

/** Sets the monthly budget (null removes it). */
export const updateMonthlyBudget = (accountId: string, monthlyBudget: number | null) =>
  updateAccount(accountId, { monthlyBudget });

/** month: "YYYY-MM"; omitted means the current month (server time). */
export const getBudgetUsage = async (accountId: string, month?: string): Promise<BudgetUsage> => {
  const res = await api.get<ApiBudgetUsage>(`/accounts/${accountId}/budget`, {
    params: month ? { month } : undefined,
  });
  const usage = res.data;
  return {
    month: usage.month,
    budget: toNumberOrNull(usage.budget),
    spent: toNumber(usage.spent),
    remaining: toNumberOrNull(usage.remaining),
    percentageUsed: toNumberOrNull(usage.percentage_used),
    status: usage.status,
  };
};
