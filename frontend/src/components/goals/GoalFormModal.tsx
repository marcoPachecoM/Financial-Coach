import React, { useState } from 'react';
import { createGoal, updateGoal } from '../../services/goals';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError } from '../../types/api';
import type { Goal } from '../../types/goal';
import { formatLocalDateToISO } from '../../utils/dateHelper';

interface GoalFormModalProps {
  isOpen: boolean;
  /** Edit mode when given. Mount with a `key` per goal so the form starts with its values. */
  goal?: Goal | null;
  onClose: () => void;
  onSaved: (goal: Goal, created: boolean) => void;
}

const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';
const labelClass = 'block text-xs font-semibold text-slate-700 mb-1 uppercase font-mono tracking-wider';

/** Create a goal or edit its name, target and optional deadline. */
export const GoalFormModal: React.FC<GoalFormModalProps> = ({ isOpen, goal = null, onClose, onSaved }) => {
  const [name, setName] = useState(goal?.name ?? '');
  const [target, setTarget] = useState(goal ? String(goal.targetAmount) : '');
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);

  if (!isOpen) return null;

  const today = formatLocalDateToISO(new Date());
  const fieldError = (field: string) => {
    const message = fieldErrors.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    const input = { name: name.trim(), targetAmount: Number(target), targetDate: targetDate || null };
    try {
      const saved = goal ? await updateGoal(goal.id, input) : await createGoal(input);
      onSaved(saved, goal === null);
      onClose();
    } catch (err: unknown) {
      // 409: another goal of the user already has this name.
      setError(getErrorMessage(err, 'Could not save the goal.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-sm w-full overflow-hidden">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <h2 className="text-base font-bold text-slate-900">{goal ? 'Edit goal' : 'New goal'}</h2>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div>
            <label className={labelClass} htmlFor="goal-name">What are you saving for?</label>
            <input
              id="goal-name"
              required
              autoFocus
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Trip to Japan"
              className={inputClass}
            />
            {fieldError('name')}
          </div>
          <div>
            <label className={labelClass} htmlFor="goal-target">Target amount</label>
            <input
              id="goal-target"
              required
              type="number"
              min="0.01"
              step="0.01"
              inputMode="decimal"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              placeholder="40000"
              className={inputClass}
            />
            {fieldError('target_amount')}
          </div>
          <div>
            <label className={labelClass} htmlFor="goal-date">Target date</label>
            <input
              id="goal-date"
              type="date"
              min={today}
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              className={inputClass}
            />
            <p className="text-[11px] text-slate-400 mt-1">Optional. With a date, we tell you how much to save per month.</p>
            {fieldError('target_date')}
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
              {saving ? 'Saving...' : goal ? 'Save' : 'Create goal'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
