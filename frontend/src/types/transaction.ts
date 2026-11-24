import type { TransactionType } from "./api";

/** A transaction as the UI uses it (converted from ApiTransaction in services/transactions.ts). */
export interface Transaction {
  id: string;
  accountId: string;
  type: TransactionType;
  /** Local date-time, e.g. "2026-01-02T13:35:00". */
  datetime: string;
  name: string;
  /**
   * Cash-flow sign, the opposite of the API: expenses negative, income positive.
   * Use `type` to tell them apart; Math.abs(amount) is the figure to show.
   */
  amount: number;
  /** One of the 26 groups: drives colors, icons, filters and charts. */
  categoryGroup: string;
  /** Free text inside the group, e.g. "Gas" in "Utilities". */
  category: string | null;
  notes: string | null;
  /** Set when the transaction is an occurrence of a recurring transaction. */
  recurringTransactionId: string | null;
  /** Tag names, alphabetical. */
  tags: string[];
}

export interface CategoryStyle {
  icon: string;
  badgeBg: string;
  description: string;
}

/** One day with transactions (GET /analytics/daily). */
export interface DailyTotal {
  /** "YYYY-MM-DD". */
  date: string;
  /** Positive. */
  expenseTotal: number;
  expenseCount: number;
  /** Of any type. */
  transactionCount: number;
}

export interface CategoryGroupSummaryItem {
  categoryGroup: string;
  /** Positive amount. */
  amount: number;
  percentage: number;
  count: number;
}
