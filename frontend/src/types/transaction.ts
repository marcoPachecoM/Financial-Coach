export type TransactionStatus = 'verified' | 'ai_approved' | 'pending';

export interface Transaction {
  id: string;
  date: string;
  provider_name: string;
  amount: number;
  mcc: string;
  status?: TransactionStatus;
}

export interface MCCCategoryInfo {
  mcc: string;
  category: string;
  icon: string;
  badgeBg: string;
  badgeText: string;
  description: string;
}
