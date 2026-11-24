import React, { useState } from 'react';
import { addContribution } from '../../services/goals';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError } from '../../types/api';
import type { Goal } from '../../types/goal';
import { formatLocalDateToISO } from '../../utils/dateHelper';
import { formatCurrency } from '../../utils/formatters';

interface AddMoneyModalProps {
  /** Open when given. */
  goal: Goal | null;
  onClose: () => void;
  onSaved: (goal: Goal) => void;
}

const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';
const labelClass = 'block text-xs font-semibold text-slate-700 mb-1 uppercase font-mono tracking-wider';

/** Record money put into a goal, or taken out of it. Mount with a `key` per goal. */
export const AddMoneyModal: React.FC<AddMoneyModalProps> = ({ goal, onClose, onSaved }) => {
  const today = formatLocalDateToISO(new Date());
  const [mode, setMode] = useState<'add' | 'withdraw'>('add');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(today);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);

  if (!goal) return null;

  const fieldError = (field: string) => {
    const message = fieldErrors.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    const value = Number(amount);
    try {
      const saved = await addContribution(goal.id, {
        amount: mode === 'add' ? value : -value,
        date,
        note: note.trim() || null,
      });
      onSaved(saved);
      onClose();
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Could not save the money.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  const tab = (value: 'add' | 'withdraw', label: string) => (
    <button
      type="button"
      role="radio"
      aria-checked={mode === value}
      onClick={() => setMode(value)}
      className={`flex-1 py-1.5 rounded-lg text-xs font-semibold cursor-pointer ${
        mode === value ? 'bg-white shadow-sm text-slate-900' : 'text-slate-500 hover:text-slate-700'
      }`}
    >
      {label}
    </button>
  );

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-sm w-full overflow-hidden">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="min-w-0">
            <h2 className="text-base font-bold text-slate-900 truncate">{goal.name}</h2>
            <p className="text-xs text-slate-500">
              {formatCurrency(goal.saved)} saved of {formatCurrency(goal.targetAmount)}
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div role="radiogroup" aria-label="Add or withdraw" className="flex gap-1 p-1 rounded-xl bg-slate-100">
            {tab('add', 'Add money')}
            {tab('withdraw', 'Withdraw')}
          </div>
          <div>
            <label className={labelClass} htmlFor="contribution-amount">Amount</label>
            <input
              id="contribution-amount"
              required
              autoFocus
              type="number"
              min="0.01"
              max={mode === 'withdraw' ? goal.saved : undefined}
              step="0.01"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="500"
              className={inputClass}
            />
            {fieldError('amount')}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass} htmlFor="contribution-date">Date</label>
              <input
                id="contribution-date"
                required
                type="date"
                max={today}
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className={inputClass}
              />
              {fieldError('date')}
            </div>
            <div>
              <label className={labelClass} htmlFor="contribution-note">Note</label>
              <input
                id="contribution-note"
                maxLength={200}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Optional"
                className={inputClass}
              />
            </div>
          </div>
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
              {saving ? 'Saving...' : mode === 'add' ? 'Add money' : 'Withdraw'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
