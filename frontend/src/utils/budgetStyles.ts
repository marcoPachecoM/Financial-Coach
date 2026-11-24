import type { BudgetStatus } from '../types/api';

/** Label and colors of each budget status: the account's budget and the category budgets. */
export const BUDGET_STATUS_STYLES: Record<BudgetStatus, { label: string; bar: string; badge: string; text: string }> = {
  ON_TRACK: {
    label: 'On track',
    bar: 'bg-emerald-500',
    badge: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    text: 'text-emerald-700',
  },
  WARNING: {
    label: 'Close to the limit',
    bar: 'bg-amber-500',
    badge: 'bg-amber-50 text-amber-700 border-amber-200',
    text: 'text-amber-700',
  },
  EXCEEDED: { label: 'Over budget', bar: 'bg-rose-500', badge: 'bg-rose-50 text-rose-700 border-rose-200', text: 'text-rose-600' },
  NO_BUDGET: { label: 'No budget', bar: 'bg-slate-300', badge: 'bg-slate-100 text-slate-600 border-slate-200', text: 'text-slate-500' },
};
