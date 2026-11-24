import React, { useState } from 'react';
import { saveCategoryBudgets } from '../../services/categoryBudgets';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError } from '../../types/api';
import type { CategoryBudgets } from '../../types/budget';
import { CATEGORY_STYLES } from '../../utils/categoryHelper';
import { formatCurrency } from '../../utils/formatters';

interface CategoryBudgetsModalProps {
  isOpen: boolean;
  accountId: string;
  /** Current budgets and this month's spending. Mount with a `key` so the form starts from them. */
  current: CategoryBudgets;
  /** The account's monthly budget, to warn when the category budgets add up to more. */
  accountBudget: number | null;
  onClose: () => void;
  onSaved: (saved: CategoryBudgets) => void;
}

interface Row {
  categoryGroup: string;
  amount: string;
}

const ALL_GROUPS = Object.keys(CATEGORY_STYLES);
const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';

/** Pick the category groups to watch and their monthly limit; saving replaces every budget. */
export const CategoryBudgetsModal: React.FC<CategoryBudgetsModalProps> = ({
  isOpen,
  accountId,
  current,
  accountBudget,
  onClose,
  onSaved,
}) => {
  const [rows, setRows] = useState<Row[]>(() =>
    current.budgets.map((b) => ({ categoryGroup: b.categoryGroup, amount: String(b.budget) })),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);

  if (!isOpen) return null;

  // This month's spending of every group, as a hint next to each row and in the picker.
  const spent = new Map<string, number>();
  current.budgets.forEach((b) => spent.set(b.categoryGroup, b.spent));
  current.unbudgeted.forEach((u) => spent.set(u.categoryGroup, u.spent));

  const chosen = new Set(rows.map((r) => r.categoryGroup));
  // Groups with spending this month first (largest first), then the rest alphabetically.
  const available = [
    ...current.unbudgeted.map((u) => u.categoryGroup).filter((g) => !chosen.has(g)),
    ...ALL_GROUPS.filter((g) => !chosen.has(g) && !spent.has(g)).sort(),
  ];

  const total = rows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
  const overAccount = accountBudget !== null && total > accountBudget;

  const fieldError = (index: number) => {
    const message = fieldErrors.find(
      (e) => e.field === `budgets[${index}].monthly_amount` || e.field === `budgets[${index}].category_group`,
    )?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const update = (index: number, amount: string) =>
    setRows((current) => current.map((r, i) => (i === index ? { ...r, amount } : r)));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    try {
      const saved = await saveCategoryBudgets(
        accountId,
        rows.map((r) => ({ categoryGroup: r.categoryGroup, monthlyAmount: Number(r.amount) })),
      );
      onSaved(saved);
      onClose();
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Could not save the budgets.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full overflow-hidden flex flex-col max-h-[90vh]">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div>
            <h2 className="text-base font-bold text-slate-900">Category budgets</h2>
            <p className="text-xs text-slate-500 mt-0.5">A monthly limit for the categories you want to watch.</p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-3 overflow-y-auto">
          {rows.length === 0 && (
            <p className="text-xs text-slate-400">No category budgets yet. Add a category below.</p>
          )}
          {rows.map((row, index) => (
            <div key={row.categoryGroup}>
              <div className="flex items-center gap-2">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-800 truncate">{row.categoryGroup}</p>
                  <p className="text-[11px] text-slate-400">
                    {formatCurrency(spent.get(row.categoryGroup) ?? 0)} spent this month
                  </p>
                </div>
                <input
                  type="number"
                  required
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  aria-label={`${row.categoryGroup} monthly budget`}
                  value={row.amount}
                  onChange={(e) => update(index, e.target.value)}
                  placeholder="Monthly limit"
                  className={`${inputClass} !w-32`}
                />
                <button
                  type="button"
                  onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                  aria-label={`Remove the ${row.categoryGroup} budget`}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 cursor-pointer"
                >
                  <span className="material-symbols-outlined !text-[18px]">delete</span>
                </button>
              </div>
              {fieldError(index)}
            </div>
          ))}

          {available.length > 0 && (
            <select
              value=""
              aria-label="Add a category"
              onChange={(e) => {
                const group = e.target.value;
                if (group) setRows((current) => [...current, { categoryGroup: group, amount: '' }]);
              }}
              className={`${inputClass} text-slate-600`}
            >
              <option value="">+ Add a category…</option>
              {available.map((group) => (
                <option key={group} value={group}>
                  {group}
                  {spent.has(group) ? ` (${formatCurrency(spent.get(group)!)} this month)` : ''}
                </option>
              ))}
            </select>
          )}

          {overAccount && (
            <p className="p-2.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
              These budgets add up to {formatCurrency(total)}, more than the account's monthly budget (
              {formatCurrency(accountBudget!)}).
            </p>
          )}
          {error && (
            <p className="p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold">{error}</p>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white px-4 py-2 rounded-xl text-xs font-semibold cursor-pointer"
            >
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
