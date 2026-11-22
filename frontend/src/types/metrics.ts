export interface MetricSummary {
  totalIncome: number;
  totalExpenses: number;
  /** totalIncome - totalExpenses: positive means money was saved. */
  net: number;
  avgDailySpend: number;
  transactionsPerDay: number;
  totalTransactionsCount: number;
  daysInRange: number;
  topCategoryName: string;
  topCategoryAmount: number;
  topCategoryPercentage: number;
}
