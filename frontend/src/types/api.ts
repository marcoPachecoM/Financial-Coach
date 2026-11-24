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
  /** Tag names, alphabetical. */
  tags: string[];
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
  tags?: string[];
}

/** GET /tags: a tag in use and how many transactions carry it. */
export interface ApiTagUsage {
  name: string;
  transactions: number;
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
  | "CATEGORY_BUDGET_EXCEEDED"
  | "CATEGORY_BUDGET_WARNING"
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
  /** First and last transaction of the period ("YYYY-MM-DD"); null when there are none. */
  first_date: string | null;
  last_date: string | null;
}

export interface ApiDailyTotal {
  date: string;
  expense_total: string;
  expense_count: number;
  transaction_count: number;
}

export interface ApiCategoryGroupBreakdown {
  category_group: string;
  amount: string;
  percentage: string;
  count: number;
}

export type BudgetStatus = "ON_TRACK" | "WARNING" | "EXCEEDED" | "NO_BUDGET";

/** GET/PUT /accounts/{id}/category-budgets. */
export interface ApiCategoryBudgets {
  account_id: string;
  month: string;
  budgets: {
    category_group: string;
    budget: string;
    spent: string;
    remaining: string;
    percentage_used: string | null;
    status: BudgetStatus;
  }[];
  unbudgeted: { category_group: string; spent: string; count: number }[];
}

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
  /** Rows without a group in the file that got one automatically (corrections, cache or AI). */
  records_categorized: number;
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

export type GoalStatus = "OVERDUE" | "BEHIND" | "ON_TRACK" | "NO_TARGET_DATE" | "COMPLETED";

/** GET /goals, and the answer of every change to a goal or its contributions. */
export interface ApiGoal {
  id: string;
  name: string;
  target_amount: string;
  target_date: string | null;
  saved: string;
  remaining: string;
  percentage: string;
  monthly_pace: string;
  required_monthly: string | null;
  projected_date: string | null;
  status: GoalStatus;
  created_at: string;
}

export interface ApiGoalContribution {
  id: string;
  /** Negative: a withdrawal. */
  amount: string;
  date: string;
  note: string | null;
  created_at: string;
}
