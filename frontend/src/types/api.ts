/**
 * Shapes of the Spring Boot API, exactly as defined in docs/openapi.yaml (snake_case,
 * money as strings). Services convert them to the view models in the other type files.
 */

export type TransactionType = "EXPENSE" | "INCOME";

export interface ApiAccount {
  id: string;
  name: string;
  monthly_budget: string | null;
  created_at: string;
  updated_at: string;
}

export interface ApiTransaction {
  id: string;
  user_id: string;
  account_id: string;
  type: TransactionType;
  /** Local date-time without timezone, e.g. "2026-01-02T13:35:00". */
  datetime: string;
  name: string;
  /** Spending perspective: EXPENSE positive, INCOME negative. */
  amount: string;
  /** One of the 26 groups (CategoryGroup enum). */
  category_group: string;
  /** Free text inside the group, e.g. "Gas". */
  category: string | null;
  notes: string | null;
  /** The recurring transaction this one is an occurrence of; null when it is not recurring. */
  recurring_transaction_id: string | null;
  created_at: string;
  updated_at: string;
}

/** Body of POST /transactions. */
export interface ApiTransactionCreateRequest {
  type: TransactionType;
  /** "2026-01-02T13:35" (datetime-local input). */
  datetime: string;
  name: string;
  /** Signed by type: EXPENSE positive, INCOME negative. */
  amount: string;
  category_group?: string;
  category?: string;
  account_id: string;
  notes?: string;
  recurrence?: { frequency: RecurrenceFrequency; end_date?: string };
}

export type RecurrenceFrequency = "WEEKLY" | "BIWEEKLY" | "MONTHLY" | "YEARLY";

export interface ApiRecurringTransaction {
  id: string;
  account_id: string;
  type: TransactionType;
  name: string;
  /** Expected amount, signed like transactions. */
  amount: string;
  category_group: string;
  category: string | null;
  notes: string | null;
  frequency: RecurrenceFrequency;
  /** "YYYY-MM-DD" */
  start_date: string;
  end_date: string | null;
  /** Next occurrence to be created automatically; null once the rule has ended. */
  next_due_date: string | null;
  created_at: string;
  updated_at: string;
}

/** Body of PATCH /recurring-transactions/{id}; null clears category, notes and end_date. */
export interface ApiRecurringTransactionUpdate {
  name?: string;
  amount?: string;
  category_group?: string;
  category?: string | null;
  notes?: string | null;
  end_date?: string | null;
}

export type AlertType =
  | "BUDGET_EXCEEDED"
  | "BUDGET_WARNING"
  | "RECURRING_MISSING"
  | "RECURRING_PRICE_CHANGED"
  | "RECURRING_UPCOMING"
  | "SUBSCRIPTION_AUDIT"
  | "RECURRING_SUGGESTED";

export type AlertSeverity = "CRITICAL" | "WARNING" | "INFO";

export type AlertAction = "CREATE_RECURRING" | "UPDATE_RECURRING_AMOUNT" | "STOP_RECURRING";

/** GET /accounts/{id}/alerts. `data` depends on `type` (see AlertType in docs/openapi.yaml). */
export interface ApiAlert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  data: Record<string, unknown>;
  action: AlertAction | null;
}

export interface ApiTransactionPage {
  content: ApiTransaction[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  has_more: boolean;
}

export interface ApiAnalyticsSummary {
  total_expenses: string;
  total_income: string;
  net: string;
  expense_count: number;
  income_count: number;
  average_expense: string;
  largest_expense: string;
}

export interface ApiCategoryGroupBreakdown {
  category_group: string;
  amount: string;
  percentage: string;
  count: number;
}

export type BudgetStatus = "ON_TRACK" | "WARNING" | "EXCEEDED" | "NO_BUDGET";

export interface ApiBudgetUsage {
  account_id: string;
  month: string;
  budget: string | null;
  spent: string;
  remaining: string | null;
  percentage_used: string | null;
  status: BudgetStatus;
}

export interface CsvUploadResponse {
  records_imported: number;
  records_duplicated: number;
  /** Rows linked to a recurring transaction (they replace the automatic occurrence). */
  recurring_matched: number;
  accounts_created: ApiAccount[];
}

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  role: "ADMIN" | "CLIENT";
  is_active: boolean;
}

export interface ApiFieldError {
  row?: number;
  field: string;
  message: string;
}

/** RFC 9457 Problem Details: every error response of the API. */
export interface ProblemDetail {
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  errors?: ApiFieldError[];
}
