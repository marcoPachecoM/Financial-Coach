import React from 'react';
import type { BudgetStatus } from '../../types/api';
import type { BudgetUsage } from '../../types/budget';
import { formatCurrency } from '../../utils/formatters';

interface BudgetCardProps {
  usage?: BudgetUsage;
  loading: boolean;
}

const STATUS_STYLES: Record<BudgetStatus, { label: string; bar: string; badge: string }> = {
  ON_TRACK: { label: 'On track', bar: 'bg-emerald-500', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  WARNING: { label: 'Close to the limit', bar: 'bg-amber-500', badge: 'bg-amber-50 text-amber-700 border-amber-200' },
  EXCEEDED: { label: 'Over budget', bar: 'bg-rose-500', badge: 'bg-rose-50 text-rose-700 border-rose-200' },
  NO_BUDGET: { label: 'No budget', bar: 'bg-slate-300', badge: 'bg-slate-100 text-slate-600 border-slate-200' },
};

const monthLabel = (month: string) =>
  new Date(`${month}-01T00:00:00`).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

/** Spending of the current month against the account's monthly budget. */
export const BudgetCard: React.FC<BudgetCardProps> = ({ usage, loading }) => {
  if (!usage) {
    return (
      <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow text-xs text-slate-400">
        {loading ? 'Loading budget...' : 'Budget unavailable.'}
      </section>
    );
  }

  const style = STATUS_STYLES[usage.status];
  const width = Math.min(100, usage.percentageUsed ?? 0);

  return (
    <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined !text-[18px] text-slate-500">savings</span>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
            Budget · {monthLabel(usage.month)}
          </p>
          <span className={`px-2 py-0.5 rounded-full text-[11px] font-medium border ${style.badge}`}>{style.label}</span>
        </div>
        <p className="text-sm text-slate-700">
          <strong className="text-slate-900">{formatCurrency(usage.spent)}</strong> spent
          {usage.budget !== null && (
            <>
              {' '}of {formatCurrency(usage.budget)}
              {usage.remaining !== null && (
                <span className={usage.remaining < 0 ? 'text-rose-600' : 'text-slate-500'}>
                  {' · '}
                  {usage.remaining < 0
                    ? `${formatCurrency(-usage.remaining)} over`
                    : `${formatCurrency(usage.remaining)} left`}
                </span>
              )}
            </>
          )}
        </p>
      </div>
      {usage.budget !== null ? (
        <div className="h-2 rounded-full bg-slate-100 overflow-hidden" title={`${usage.percentageUsed ?? 0}% used`}>
          <div className={`h-full rounded-full transition-all ${style.bar}`} style={{ width: `${width}%` }} />
        </div>
      ) : (
        <p className="text-xs text-slate-400">Set a monthly budget on this account to track it here.</p>
      )}
    </section>
  );
};
