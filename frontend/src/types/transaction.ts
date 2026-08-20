export type TransactionStatus = 'verified' | 'ai_approved' | 'pending';

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

export interface TransactionsApiResponse {
  status: string;
  transactions: Transaction[];
}

export interface CategoryStyle {
  icon: string;
  badgeBg: string;
  description: string;
}

export interface CategorySummaryItem {
  category: string;
  amount: number;
  percentage: number;
  count: number;
}