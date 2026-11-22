import api from "./instance";
import { toNumber } from "./money";
import type { ApiRecurringTransaction, ApiRecurringTransactionUpdate, RecurrenceFrequency } from "../types/api";
import type { RecurringTransaction } from "../types/recurring";

const toRecurring = (rule: ApiRecurringTransaction): RecurringTransaction => ({
  id: rule.id,
  accountId: rule.account_id,
  type: rule.type,
  name: rule.name,
  amount: Math.abs(toNumber(rule.amount)),
  categoryGroup: rule.category_group,
  category: rule.category,
  frequency: rule.frequency,
  startDate: rule.start_date,
  endDate: rule.end_date,
  nextDueDate: rule.next_due_date,
});

export const getRecurringTransactions = async (accountId: string): Promise<RecurringTransaction[]> => {
  const res = await api.get<ApiRecurringTransaction[]>("/recurring-transactions", {
    params: { account_id: accountId },
  });
  return res.data.map(toRecurring);
};

/** Makes an existing transaction recurring; due occurrences are created at once. */
export const makeRecurring = async (
  transactionId: string,
  frequency: RecurrenceFrequency,
  endDate?: string,
): Promise<RecurringTransaction> => {
  const res = await api.post<ApiRecurringTransaction>("/recurring-transactions", {
    transaction_id: transactionId,
    frequency,
    ...(endDate ? { end_date: endDate } : {}),
  });
  return toRecurring(res.data);
};

/** Changes apply to the occurrences created from now on. */
export const updateRecurring = async (
  id: string,
  changes: ApiRecurringTransactionUpdate,
): Promise<RecurringTransaction> => {
  const res = await api.patch<ApiRecurringTransaction>(`/recurring-transactions/${id}`, changes);
  return toRecurring(res.data);
};

/** Stops creating occurrences after `endDate` ("YYYY-MM-DD"). */
export const stopRecurring = (id: string, endDate: string) => updateRecurring(id, { end_date: endDate });

/** The transactions already created are kept as ordinary ones. */
export const deleteRecurring = async (id: string): Promise<void> => {
  await api.delete(`/recurring-transactions/${id}`);
};
