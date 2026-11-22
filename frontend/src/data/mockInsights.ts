import type { ChatMessage } from '../types/copilot';

export const INITIAL_CHAT_MESSAGES: ChatMessage[] = [
  {
    id: 'msg-1',
    sender: 'assistant',
    text: "Hi! Ask me about your spending, budget or subscriptions. I answer with the data of the selected account.",
    timestamp: '',
  },
];

export const SUGGESTION_CHIPS = [
  'Analyze my expenses',
  'Budget recommendations',
  'Subscription audit',
  'Forecast month end',
];
