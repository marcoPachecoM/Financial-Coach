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
  /** Tokens the LLM spent on this answer (only answers from POST /copilot/chat). */
  tokens?: number;
}

/** Response of POST /copilot/chat. */
export interface ApiChatResponse {
  response: string;
  provider: string;
  /** Model that answered, as the provider reports it (may be null). */
  model: string | null;
  usage: ApiTokenUsage;
}

/** Tokens spent on one message, summed over every LLM call (tool calls make several). */
export interface ApiTokenUsage {
  input_tokens: number;
  output_tokens: number;
  /** As the provider bills them; may exceed input + output (thinking tokens). */
  total_tokens: number;
  llm_calls: number;
}

/** Last event of a streamed answer (POST /copilot/chat/stream). */
export interface ApiChatStreamDone {
  provider: string;
  model: string | null;
  usage: ApiTokenUsage;
}
