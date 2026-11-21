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
