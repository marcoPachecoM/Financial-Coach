export type TransactionStatus = "verified" | "ai_approved" | "pending";

export interface Transaction {
  id: string;
  user_id: string;
  connection_id: string;
  date: string;
  provider_name: string;
  amount: number;
  mcc: number | null;
  category: string;
  status?: TransactionStatus;
}

export interface TransactionQueryParams {
  connection_id?: string;
  start_date?: string;
  end_date?: string;
  mcc?: number;
  page?: number;
  size?: number;
}

export interface CategoryStyle {
  icon: string;
  badgeBg: string;
  description: string;
}

export interface CategoryQueryParams {
  connection_id: number;

  group_by?: string;

  start_date?: string;

  end_date?: string;
}

export interface CategorySummaryItem {
  category: string;
  amount: number;
  percentage: number;
  count: number;
}
