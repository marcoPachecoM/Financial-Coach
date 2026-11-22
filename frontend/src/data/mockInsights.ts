import type { ChatMessage } from '../types/copilot';

export const INITIAL_CHAT_MESSAGES: ChatMessage[] = [
  {
    id: 'msg-1',
    sender: 'assistant',
    text: "Hello Alex! I've analyzed your latest transactions. Everything looks on track for your 20% savings target this month.",
    timestamp: '9:00 AM',
  },
];

export const SUGGESTION_CHIPS = [
  'Analyze my expenses',
  'Budget recommendations',
  'Subscription audit',
  'Forecast month end',
];
