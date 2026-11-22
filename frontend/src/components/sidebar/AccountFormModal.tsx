import React, { useState } from 'react';
import { createAccount, updateAccount } from '../../services/accounts';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { Account } from '../../types/account';
import type { ApiFieldError } from '../../types/api';

interface AccountFormModalProps {
  isOpen: boolean;
  /** Edit mode when given. Mount with a `key` per account so the form starts with its values. */
  account?: Account | null;
  onClose: () => void;
  onSaved: (account: Account, created: boolean) => void;
}

const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';
const labelClass = 'block text-xs font-semibold text-slate-700 mb-1 uppercase font-mono tracking-wider';

/** Create an account or edit its name and monthly budget. */
export const AccountFormModal: React.FC<AccountFormModalProps> = ({ isOpen, account = null, onClose, onSaved }) => {
  const [name, setName] = useState(account?.name ?? '');
  const [budget, setBudget] = useState(account?.monthlyBudget != null ? String(account.monthlyBudget) : '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);

  if (!isOpen) return null;

  const fieldError = (field: string) => {
    const message = fieldErrors.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    const monthlyBudget = budget.trim() === '' ? null : Number(budget);
    try {
      const saved = account
        ? await updateAccount(account.id, { name: name.trim(), monthlyBudget })
        : await createAccount(name.trim(), monthlyBudget);
      onSaved(saved, account === null);
      onClose();
    } catch (err: unknown) {
      // 409: the name is already used by another of the user's accounts.
      setError(getErrorMessage(err, 'Could not save the account.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-sm w-full overflow-hidden">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <h2 className="text-base font-bold text-slate-900">{account ? 'Edit account' : 'New account'}</h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div>
            <label className={labelClass} htmlFor="account-name">Name</label>
            <input
              id="account-name"
              required
              autoFocus
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Credit card"
              className={inputClass}
            />
            {fieldError('name')}
          </div>
          <div>
            <label className={labelClass} htmlFor="account-budget">Monthly budget</label>
            <input
              id="account-budget"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={budget}
              onChange={(e) => setBudget(e.target.value)}
              placeholder="Empty = no budget"
              className={inputClass}
            />
            {fieldError('monthly_budget')}
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
              {saving ? 'Saving...' : account ? 'Save' : 'Create account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
