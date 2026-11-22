import type { AlertAction } from './api';

export type InsightSeverity = 'critical' | 'warning' | 'info';

export interface InsightAction {
  id: AlertAction;
  label: string;
}

/** An alert from the API, ready to show in the copilot panel (see utils/alertMessages.ts). */
export interface Insight {
  /** The alert id: stable, so a dismissed alert stays dismissed. */
  id: string;
  severity: InsightSeverity;
  icon: string;
  title: string;
  description: string;
  actions: InsightAction[];
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  timestamp: string;
  isActionable?: boolean;
}

/** Response of POST /copilot/chat. */
export interface ApiChatResponse {
  response: string;
  provider: string;
  /** Model that answered, as the provider reports it (may be null). */
  model: string | null;
}
