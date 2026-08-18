import type { Insight, ChatMessage } from '../types/copilot';

export const INITIAL_INSIGHTS: Insight[] = [
  {
    id: 'ins-1',
    type: 'optimization',
    title: 'Optimization found: Move $200 to high-yield savings.',
    description: "Based on your recent lower spending in 'Dining Out', you have a surplus.",
    actionText: 'Execute',
    status: 'active',
    category: 'Savings',
    amount: 200,
  },
  {
    id: 'ins-2',
    type: 'alert',
    title: 'Budget Alert: You are nearing your Entertainment limit for May.',
    description: 'Current: $350 / $400 (87.5% utilized)',
    status: 'active',
    category: 'Entertainment',
  },
  {
    id: 'ins-3',
    type: 'info',
    title: 'Subscription audit completed.',
    description: '2 recurring charges (Netflix & Spotify) verified with zero price increases.',
    status: 'active',
    category: 'Subscriptions',
  },
];

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
