import React, { useState } from 'react';
import type { CategoryBudgets } from '../../types/budget';
import { formatCurrency } from '../../utils/formatters';
import { BUDGET_STATUS_STYLES } from '../../utils/budgetStyles';
import { getCategoryStyle } from '../../utils/categoryHelper';

interface CategoryBudgetsCardProps {
  data?: CategoryBudgets;
  loading: boolean;
  onManage: () => void;
}

/** Rows shown before "Show more": the card keeps the same height however many budgets there are. */
const VISIBLE_ROWS = 4;

const monthLabel = (month: string) =>
  new Date(`${month}-01T00:00:00`).toLocaleDateString('en-US', { month: 'long' });

/**
 * This month's spending of the category groups that have a budget, closest to (or over) the
 * limit first. Only the budgeted groups appear; the rest is summed up in one line.
 */
export const CategoryBudgetsCard: React.FC<CategoryBudgetsCardProps> = ({ data, loading, onManage }) => {
  const [expanded, setExpanded] = useState(false);

  if (!data) {
    return (
      <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow text-xs text-slate-400">
        {loading ? 'Loading category budgets...' : 'Category budgets unavailable.'}
      </section>
    );
  }

  const rows = expanded ? data.budgets : data.budgets.slice(0, VISIBLE_ROWS);
  const hidden = data.budgets.length - rows.length;
  const unbudgetedTotal = data.unbudgeted.reduce((sum, u) => sum + u.spent, 0);

  return (
    <section className="bg-white rounded-2xl px-6 py-4 border border-slate-200/80 card-shadow">
      <div className="flex items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined !text-[18px] text-slate-500">donut_small</span>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
            Category budgets · {monthLabel(data.month)}
          </p>
        </div>
        <button
          onClick={onManage}
          className="text-xs font-semibold text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 px-2 py-1 rounded-lg cursor-pointer"
        >
          {data.budgets.length === 0 ? 'Set budgets' : 'Manage'}
        </button>
      </div>

      {data.budgets.length === 0 ? (
        <p className="text-xs text-slate-400">
          Pick the categories you want to watch (e.g. Food, Entertainment) and give each a monthly limit.
        </p>
      ) : (
        <ul className="space-y-2.5">
          {rows.map((b) => {
            const style = BUDGET_STATUS_STYLES[b.status];
            const width = b.budget === 0 ? (b.spent > 0 ? 100 : 0) : Math.min(100, (b.spent / b.budget) * 100);
            return (
              <li key={b.categoryGroup} className="grid grid-cols-[minmax(0,8rem)_1fr_auto] items-center gap-3">
                <span className="flex items-center gap-1.5 text-sm text-slate-800 truncate" title={b.categoryGroup}>
                  <span className="material-symbols-outlined !text-[16px] text-slate-400" aria-hidden="true">
                    {getCategoryStyle(b.categoryGroup).icon}
                  </span>
                  <span className="truncate">{b.categoryGroup}</span>
                </span>
                <div
                  className="h-1.5 rounded-full bg-slate-100 overflow-hidden"
                  role="progressbar"
                  aria-label={`${b.categoryGroup} budget used`}
                  aria-valuenow={Math.round(width)}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  title={style.label}
                >
                  <div className={`h-full rounded-full transition-all ${style.bar}`} style={{ width: `${width}%` }} />
                </div>
                <span className="text-xs text-slate-600 whitespace-nowrap text-right">
                  <span className={`font-semibold ${style.text}`}>
                    {b.percentageUsed !== null ? `${Math.round(b.percentageUsed)}%` : 'Over'}
                  </span>{' '}
                  <span className="hidden sm:inline">
                    {formatCurrency(b.spent)} / {formatCurrency(b.budget)}
                  </span>
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {(hidden > 0 || expanded) && data.budgets.length > VISIBLE_ROWS && (
        <button
          onClick={() => setExpanded((v) => !v)}
          className="mt-2 text-xs font-medium text-slate-500 hover:text-slate-700 cursor-pointer"
        >
          {expanded ? 'Show less' : `Show ${hidden} more`}
        </button>
      )}

      {data.budgets.length > 0 && data.unbudgeted.length > 0 && (
        <p className="mt-3 pt-3 border-t border-slate-100 text-xs text-slate-500">
          Without a budget: <strong className="text-slate-700">{formatCurrency(unbudgetedTotal)}</strong> in{' '}
          {data.unbudgeted.length} {data.unbudgeted.length === 1 ? 'category' : 'categories'}
        </p>
      )}
    </section>
  );
};
