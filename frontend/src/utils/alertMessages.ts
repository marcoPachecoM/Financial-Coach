import type { AlertAction, ApiAlert, RecurrenceFrequency } from '../types/api';
import type { Insight, InsightSeverity } from '../types/copilot';
import { FREQUENCY_LABELS } from '../types/recurring';
import { formatCurrency, formatFullDate } from './formatters';

/** Money in alert data is a signed string ("199.00", income "-20000.00"); show the figure. */
const money = (value: unknown) => formatCurrency(Math.abs(Number(value)));
const text = (value: unknown) => String(value ?? '');
const date = (value: unknown) => formatFullDate(text(value));
const monthName = (value: unknown) =>
  new Date(`${text(value)}-01T00:00:00`).toLocaleDateString('en-US', { month: 'long' });
const percent = (value: unknown) => `${Number(value)}%`;

/** "Netflix", "Netflix & Spotify", "Netflix, Spotify & Gym". */
const joinNames = (names: string[]) =>
  names.length <= 1 ? names.join('') : `${names.slice(0, -1).join(', ')} & ${names[names.length - 1]}`;

const SEVERITY: Record<ApiAlert['severity'], InsightSeverity> = {
  CRITICAL: 'critical',
  WARNING: 'warning',
  INFO: 'info',
};

const ACTION_LABELS: Record<AlertAction, string> = {
  CREATE_RECURRING: 'Mark as recurring',
  UPDATE_RECURRING_AMOUNT: 'Update amount',
  STOP_RECURRING: 'Stop recurring',
};

/** Title, description and icon for each alert type; the API only sends structured data. */
const describe = (alert: ApiAlert): Pick<Insight, 'icon' | 'title' | 'description'> => {
  const d = alert.data;
  switch (alert.type) {
    case 'BUDGET_WARNING':
      return {
        icon: 'warning',
        title: `Budget Alert: You are nearing your limit for ${monthName(d.month)}.`,
        description: `Current: ${money(d.spent)} / ${money(d.budget)} (${percent(d.percentage_used)} utilized)`,
      };
    case 'BUDGET_EXCEEDED':
      return {
        icon: 'error',
        title: `Budget Alert: You are over your limit for ${monthName(d.month)}.`,
        description:
          `Spent ${money(d.spent)} of ${money(d.budget)}` +
          (d.percentage_used !== null ? ` (${percent(d.percentage_used)} utilized)` : '') +
          ` · ${money(d.remaining)} over budget.`,
      };
    case 'RECURRING_MISSING':
      return {
        icon: 'search_off',
        title: `${text(d.name)} is missing from your bank statement.`,
        description: `The ${money(d.amount)} charge due ${date(d.due_date)} was added automatically, but your imported statement doesn't show it. Did you cancel it?`,
      };
    case 'RECURRING_PRICE_CHANGED': {
      const change = Number(d.change_percentage);
      return {
        icon: change > 0 ? 'trending_up' : 'trending_down',
        title: `Price change: ${text(d.name)} charged ${money(d.charged_amount)} instead of ${money(d.expected_amount)}.`,
        description: `${change > 0 ? '+' : ''}${percent(d.change_percentage)} on the charge due ${date(d.due_date)}. Update the recurring amount to keep tracking it.`,
      };
    }
    case 'RECURRING_UPCOMING':
      return {
        icon: 'event_upcoming',
        title: `Upcoming: ${text(d.name)} on ${date(d.due_date)}.`,
        description: `${money(d.amount)} will be added automatically.`,
      };
    case 'SUBSCRIPTION_AUDIT': {
      const names = Array.isArray(d.names) ? d.names.map(text) : [];
      const count = Number(d.count);
      return {
        icon: 'verified',
        title: 'Subscription audit completed.',
        description: `${count} recurring charge${count === 1 ? '' : 's'} (${joinNames(names)}) verified with zero price increases.`,
      };
    }
    case 'RECURRING_SUGGESTED': {
      const frequency = FREQUENCY_LABELS[d.frequency as RecurrenceFrequency] ?? text(d.frequency);
      return {
        icon: 'autorenew',
        title: `Looks like a subscription: ${text(d.name)}.`,
        description: `${Number(d.occurrences)} charges of about ${money(d.amount)} (${frequency.toLowerCase()}). Mark it as recurring to add the next ones automatically.`,
      };
    }
  }
};

export const toInsight = (alert: ApiAlert): Insight => ({
  id: alert.id,
  severity: SEVERITY[alert.severity],
  ...describe(alert),
  actions: alert.action ? [{ id: alert.action, label: ACTION_LABELS[alert.action] }] : [],
});
