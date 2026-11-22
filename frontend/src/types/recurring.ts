import type { RecurrenceFrequency, TransactionType } from "./api";

/** A recurring transaction as the UI uses it (converted in services/recurring.ts). */
export interface RecurringTransaction {
  id: string;
  accountId: string;
  type: TransactionType;
  name: string;
  /** Positive figure to show; `type` says whether it is an expense or income. */
  amount: number;
  categoryGroup: string;
  category: string | null;
  frequency: RecurrenceFrequency;
  startDate: string;
  endDate: string | null;
  /** null once the rule has ended. */
  nextDueDate: string | null;
}

export const FREQUENCY_LABELS: Record<RecurrenceFrequency, string> = {
  WEEKLY: "Weekly",
  BIWEEKLY: "Every 2 weeks",
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
};
