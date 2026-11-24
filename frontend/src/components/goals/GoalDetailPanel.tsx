import React, { useEffect, useState } from 'react';
import { useAsync } from '../../hooks/useAsync';
import { deleteContribution, getContributions } from '../../services/goals';
import { getErrorMessage } from '../../services/errors';
import type { Goal } from '../../types/goal';
import { formatLocalDateToISO } from '../../utils/dateHelper';
import { formatCurrency, formatFullDate } from '../../utils/formatters';
import { GOAL_STATUS_STYLES, monthYear } from '../../utils/goalStyles';
import { GoalChart } from './GoalChart';

interface GoalDetailPanelProps {
  goal: Goal;
  /** Changes whenever the goal changed elsewhere (e.g. money added): the history reloads. */
  version: number;
  onClose: () => void;
  onAddMoney: () => void;
  onChanged: () => void;
}

/** Side drawer (full screen on phones): the chart, the key numbers and the contribution history. */
export const GoalDetailPanel: React.FC<GoalDetailPanelProps> = ({ goal, version, onClose, onAddMoney, onChanged }) => {
  const history = useAsync(() => getContributions(goal.id), `${goal.id}|${version}`);
  const contributions = history.data ?? history.previousData ?? [];
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const style = GOAL_STATUS_STYLES[goal.status];

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const remove = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await deleteContribution(goal.id, id);
      onChanged();
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Could not delete it.'));
    } finally {
      setBusyId(null);
    }
  };

  const stat = (label: string, value: string, hint?: string) => (
    <div className="p-3 rounded-xl bg-slate-50 border border-slate-100">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500 font-mono">{label}</p>
      <p className="text-sm font-bold text-slate-900 mt-0.5">{value}</p>
      {hint && <p className="text-[11px] text-slate-500">{hint}</p>}
    </div>
  );

  return (
    <div className="fixed inset-0 z-[60] flex justify-end">
      <div className="absolute inset-0 bg-slate-900/40 animate-in fade-in duration-150" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={goal.name}
        className="relative w-full sm:max-w-lg h-full bg-white shadow-2xl flex flex-col animate-in slide-in-from-right duration-200"
      >
        <header className="p-5 border-b border-slate-100 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-lg font-bold text-slate-900 truncate">{goal.name}</h2>
            <span className={`mt-1 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border ${style.badge}`}>
              <span aria-hidden="true" className="material-symbols-outlined !text-[13px]">{style.icon}</span>
              {style.label}
            </span>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          <GoalChart goal={goal} contributions={contributions} today={formatLocalDateToISO(new Date())} />

          <div className="grid grid-cols-2 gap-2">
            {stat('Saved', formatCurrency(goal.saved), `${Math.round(goal.percentage)}% of ${formatCurrency(goal.targetAmount)}`)}
            {stat('To go', formatCurrency(goal.remaining))}
            {stat('Your pace', `${formatCurrency(goal.monthlyPace)}/mo`, 'Last 3 months')}
            {goal.requiredMonthly !== null
              ? stat('Needed', `${formatCurrency(goal.requiredMonthly)}/mo`, `To arrive by ${monthYear(goal.targetDate!)}`)
              : stat(
                  'Projected',
                  goal.status === 'COMPLETED' ? 'Done 🎉' : goal.projectedDate ? monthYear(goal.projectedDate) : '—',
                  goal.projectedDate || goal.status === 'COMPLETED' ? undefined : 'Add money to see it',
                )}
          </div>
          {goal.requiredMonthly !== null && (
            <p className="text-xs text-slate-600">
              {goal.projectedDate
                ? `At your pace you reach it in ${monthYear(goal.projectedDate)}.`
                : 'Add money to see when you would reach it.'}
            </p>
          )}

          <section>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-bold text-slate-900">History</h3>
              {goal.status !== 'COMPLETED' && (
                <button
                  onClick={onAddMoney}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold cursor-pointer"
                >
                  <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">add</span>
                  Add money
                </button>
              )}
            </div>
            {error && <p className="mb-2 text-xs text-rose-600" role="alert">{error}</p>}
            {history.loading && contributions.length === 0 ? (
              <p className="text-xs text-slate-400">Loading...</p>
            ) : contributions.length === 0 ? (
              <p className="text-xs text-slate-400">No money added yet.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {contributions.map((c) => (
                  <li key={c.id} className="py-2 flex items-center gap-3">
                    <div className="flex-1 min-w-0">
                      <p className={`text-sm font-semibold ${c.amount < 0 ? 'text-rose-600' : 'text-emerald-700'}`}>
                        {c.amount < 0 ? '−' : '+'}
                        {formatCurrency(Math.abs(c.amount))}
                      </p>
                      <p className="text-[11px] text-slate-500 truncate">
                        {formatFullDate(c.date)}
                        {c.note ? ` · ${c.note}` : ''}
                      </p>
                    </div>
                    <button
                      onClick={() => remove(c.id)}
                      disabled={busyId === c.id}
                      aria-label={`Delete the ${formatCurrency(Math.abs(c.amount))} of ${formatFullDate(c.date)}`}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-50 cursor-pointer"
                    >
                      <span className="material-symbols-outlined !text-[18px]">delete</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </aside>
    </div>
  );
};
