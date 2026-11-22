import React from 'react';
import type { RecurringTransaction } from '../../types/recurring';
import { FREQUENCY_LABELS } from '../../types/recurring';
import { getCategoryStyle } from '../../utils/categoryHelper';
import { formatCurrency, formatFullDate } from '../../utils/formatters';

interface RecurringCardProps {
  rules: RecurringTransaction[];
  loading: boolean;
  /** Rule whose action is running, if any. */
  busyId: string | null;
  onStop: (rule: RecurringTransaction) => void;
  onDelete: (rule: RecurringTransaction) => void;
}

/** Recurring transactions of the account: what is added automatically and when. */
export const RecurringCard: React.FC<RecurringCardProps> = ({ rules, loading, busyId, onStop, onDelete }) => {
  const active = rules.filter((r) => r.nextDueDate !== null);
  const monthlyExpenses = active
    .filter((r) => r.type === 'EXPENSE' && r.frequency === 'MONTHLY')
    .reduce((sum, r) => sum + r.amount, 0);

  return (
    <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined !text-[18px] text-slate-500">event_repeat</span>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">Recurring</p>
          <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
            {active.length} active
          </span>
        </div>
        {monthlyExpenses > 0 && (
          <p className="text-sm text-slate-700">
            <strong className="text-slate-900">{formatCurrency(monthlyExpenses)}</strong> / month in subscriptions
          </p>
        )}
      </div>

      {rules.length === 0 ? (
        <p className="text-xs text-slate-400">
          {loading
            ? 'Loading recurring transactions...'
            : 'Nothing recurring yet. Choose "Repeat" when adding a transaction, or "Make recurring" in the table.'}
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {rules.map((rule) => {
            const style = getCategoryStyle(rule.categoryGroup);
            const ended = rule.nextDueDate === null;
            const busy = busyId === rule.id;
            return (
              <li key={rule.id} className={`py-2.5 flex items-center gap-3 ${ended ? 'opacity-60' : ''}`}>
                <div className={`w-8 h-8 rounded-lg border flex items-center justify-center shrink-0 ${style.badgeBg}`}>
                  <span className="material-symbols-outlined !text-[16px]">{style.icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-slate-900 truncate">{rule.name}</p>
                  <p className="text-[11px] text-slate-500 font-mono">
                    {FREQUENCY_LABELS[rule.frequency]} ·{' '}
                    {ended
                      ? `ended${rule.endDate ? ` ${formatFullDate(rule.endDate)}` : ''}`
                      : `next ${formatFullDate(rule.nextDueDate!)}`}
                  </p>
                </div>
                <span
                  className={`font-mono text-sm font-semibold ${rule.type === 'EXPENSE' ? 'text-slate-900' : 'text-emerald-600'}`}
                >
                  {rule.type === 'EXPENSE' ? '' : '+'}
                  {formatCurrency(rule.amount)}
                </span>
                <div className="flex items-center gap-1">
                  {!ended && (
                    <button
                      disabled={busy}
                      onClick={() => onStop(rule)}
                      title="Stop: no more occurrences after today"
                      className="p-1.5 rounded-lg text-slate-400 hover:text-amber-600 hover:bg-amber-50 disabled:opacity-40 cursor-pointer"
                    >
                      <span className="material-symbols-outlined !text-[18px]">pause_circle</span>
                    </button>
                  )}
                  <button
                    disabled={busy}
                    onClick={() => onDelete(rule)}
                    title="Delete: the transactions already added are kept"
                    className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-40 cursor-pointer"
                  >
                    <span className="material-symbols-outlined !text-[18px]">delete</span>
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
};
