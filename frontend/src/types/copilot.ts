export type InsightType = 'optimization' | 'alert' | 'info';
export type InsightStatus = 'active' | 'executed' | 'dismissed';

export interface Insight {
  id: string;
  type: InsightType;
  title: string;
  description: string;
  actionText?: string;
  status: InsightStatus;
  category?: string;
  amount?: number;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  timestamp: string;
  isActionable?: boolean;
}
