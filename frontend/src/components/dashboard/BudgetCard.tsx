import React, { useState } from 'react';
import type { BudgetUsage } from '../../types/budget';
import { formatCurrency } from '../../utils/formatters';
import { BUDGET_STATUS_STYLES } from '../../utils/budgetStyles';

interface BudgetCardProps {
  usage?: BudgetUsage;
  loading: boolean;
  /** Saves the account's monthly budget (null removes it). Rejects with a message on error. */
  onSaveBudget: (monthlyBudget: number | null) => Promise<void>;
}

const monthLabel = (month: string) =>
  new Date(`${month}-01T00:00:00`).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

/** Spending of the current month against the account's monthly budget. */
export const BudgetCard: React.FC<BudgetCardProps> = ({ usage, loading, onSaveBudget }) => {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!usage) {
    return (
      <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow text-xs text-slate-400">
        {loading ? 'Loading budget...' : 'Budget unavailable.'}
      </section>
    );
  }

  const style = BUDGET_STATUS_STYLES[usage.status];
  const width = Math.min(100, usage.percentageUsed ?? 0);

  const startEditing = () => {
    setValue(usage.budget !== null ? String(usage.budget) : '');
    setError(null);
    setEditing(true);
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onSaveBudget(value.trim() === '' ? null : Number(value));
      setEditing(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not save the budget.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined !text-[18px] text-slate-500">savings</span>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
            Budget · {monthLabel(usage.month)}
          </p>
          <span className={`px-2 py-0.5 rounded-full text-[11px] font-medium border ${style.badge}`}>{style.label}</span>
          {!editing && (
            <button
              onClick={startEditing}
              title="Edit monthly budget"
              className="p-1 rounded-lg text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 cursor-pointer"
            >
              <span className="material-symbols-outlined !text-[16px]">edit</span>
            </button>
          )}
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
      {editing && (
        <form onSubmit={save} className="flex flex-wrap items-center gap-2 mb-3">
          <input
            autoFocus
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="Monthly budget (empty = none)"
            className="text-sm px-3 py-1.5 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden w-56"
          />
          <button
            type="submit"
            disabled={saving}
            className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white px-3 py-1.5 rounded-xl text-xs font-semibold cursor-pointer"
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
          <button
            type="button"
            onClick={() => setEditing(false)}
            className="px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
          >
            Cancel
          </button>
          {error && <p className="w-full text-[11px] text-rose-600">{error}</p>}
        </form>
      )}
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
