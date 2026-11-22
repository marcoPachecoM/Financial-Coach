import React, { useState } from 'react';
import { createTransaction, updateTransaction } from '../../services/transactions';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError, RecurrenceFrequency, TransactionType } from '../../types/api';
import type { Transaction } from '../../types/transaction';
import { FREQUENCY_LABELS } from '../../types/recurring';
import { CATEGORY_STYLES } from '../../utils/categoryHelper';

interface TransactionFormModalProps {
  isOpen: boolean;
  accountId: string | null;
  accountName: string;
  /** Edit mode: the form starts with its values and saves with PATCH. Mount with a `key` per transaction. */
  transaction?: Transaction | null;
  onClose: () => void;
  /** Called after the transaction (and its recurrence, if any) was created or updated. */
  onSaved: (transaction: Transaction, repeated: RecurrenceFrequency | null) => void;
}

const GROUPS = Object.keys(CATEGORY_STYLES);
const FREQUENCIES = Object.keys(FREQUENCY_LABELS) as RecurrenceFrequency[];

/** Current local time as the datetime-local input expects it: "YYYY-MM-DDTHH:mm". */
const nowForInput = () => {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const EMPTY = {
  type: 'EXPENSE' as TransactionType,
  name: '',
  amount: '',
  categoryGroup: '',
  category: '',
  notes: '',
  repeat: '' as RecurrenceFrequency | '',
  endDate: '',
};

const fromTransaction = (tx: Transaction): typeof EMPTY => ({
  ...EMPTY,
  type: tx.type,
  name: tx.name,
  amount: Math.abs(tx.amount).toFixed(2),
  categoryGroup: tx.categoryGroup,
  category: tx.category ?? '',
  notes: tx.notes ?? '',
});

const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';
const labelClass = 'block text-xs font-semibold text-slate-700 mb-1 uppercase font-mono tracking-wider';

export const TransactionFormModal: React.FC<TransactionFormModalProps> = ({
  isOpen,
  accountId,
  accountName,
  transaction = null,
  onClose,
  onSaved,
}) => {
  const isEdit = transaction !== null;
  const [form, setForm] = useState(() => (transaction ? fromTransaction(transaction) : EMPTY));
  const [datetime, setDatetime] = useState(() => (transaction ? transaction.datetime.slice(0, 16) : nowForInput()));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);

  if (!isOpen) return null;

  const set = <K extends keyof typeof EMPTY>(key: K, value: (typeof EMPTY)[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  /** Error of a field; nested names ("recurrence.frequency") also match their last part. */
  const errorOf = (field: string) =>
    fieldErrors.find((e) => e.field === field || e.field.endsWith(`.${field}`))?.message;

  const close = () => {
    setForm(EMPTY);
    setDatetime(nowForInput());
    setError(null);
    setFieldErrors([]);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetAccount = transaction?.accountId ?? accountId;
    if (!targetAccount) return;
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    try {
      const input = {
        accountId: targetAccount,
        type: form.type,
        datetime,
        name: form.name.trim(),
        amount: Number(form.amount),
        categoryGroup: form.categoryGroup || undefined,
        category: form.category,
        notes: form.notes,
        recurrence: form.repeat ? { frequency: form.repeat, endDate: form.endDate || undefined } : undefined,
      };
      const saved = transaction ? await updateTransaction(transaction.id, input) : await createTransaction(input);
      onSaved(saved, isEdit ? null : form.repeat || null);
      close();
    } catch (err: unknown) {
      setError(getErrorMessage(err, isEdit ? 'Could not save the transaction.' : 'Could not create the transaction.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  const fieldError = (field: string) => {
    const message = errorOf(field);
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[24px]">{isEdit ? 'edit' : 'add_card'}</span>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 font-sans">{isEdit ? 'Edit Transaction' : 'Add Transaction'}</h2>
              <p className="text-xs text-slate-500">
                Account: <strong className="text-slate-700">{accountName}</strong>
              </p>
            </div>
          </div>
          <button
            onClick={close}
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-y-auto">
          {/* Type */}
          <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-xl">
            {(['EXPENSE', 'INCOME'] as TransactionType[]).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => set('type', type)}
                className={`py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                  form.type === type ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {type === 'EXPENSE' ? 'Expense' : 'Income'}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className={labelClass} htmlFor="tx-name">Merchant</label>
              <input
                id="tx-name"
                required
                maxLength={255}
                value={form.name}
                onChange={(e) => set('name', e.target.value)}
                placeholder="Netflix"
                className={inputClass}
              />
              {fieldError('name')}
            </div>
            <div>
              <label className={labelClass} htmlFor="tx-amount">Amount</label>
              <input
                id="tx-amount"
                required
                type="number"
                min="0.01"
                step="0.01"
                inputMode="decimal"
                value={form.amount}
                onChange={(e) => set('amount', e.target.value)}
                placeholder="199.00"
                className={inputClass}
              />
              {fieldError('amount')}
            </div>
          </div>

          <div>
            <label className={labelClass} htmlFor="tx-datetime">Date</label>
            <input
              id="tx-datetime"
              required
              type="datetime-local"
              value={datetime}
              onChange={(e) => setDatetime(e.target.value)}
              className={inputClass}
            />
            {fieldError('datetime')}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className={labelClass} htmlFor="tx-group">Category group</label>
              <select
                id="tx-group"
                value={form.categoryGroup}
                onChange={(e) => set('categoryGroup', e.target.value)}
                className={inputClass}
              >
                <option value="">Automatic</option>
                {GROUPS.map((group) => (
                  <option key={group} value={group}>
                    {group}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass} htmlFor="tx-category">Category</label>
              <input
                id="tx-category"
                maxLength={100}
                value={form.category}
                onChange={(e) => set('category', e.target.value)}
                placeholder="Streaming"
                className={inputClass}
              />
            </div>
          </div>

          {/* Recurrence (only when creating: an existing rule is managed in the Recurring card) */}
          {!isEdit && (
          <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3.5 space-y-3">
            <div>
              <label className={labelClass} htmlFor="tx-repeat">
                <span className="inline-flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[14px]">repeat</span>
                  Repeat
                </span>
              </label>
              <select
                id="tx-repeat"
                value={form.repeat}
                onChange={(e) => set('repeat', e.target.value as RecurrenceFrequency | '')}
                className={inputClass}
              >
                <option value="">Doesn't repeat</option>
                {FREQUENCIES.map((frequency) => (
                  <option key={frequency} value={frequency}>
                    {FREQUENCY_LABELS[frequency]}
                  </option>
                ))}
              </select>
            </div>
            {form.repeat && (
              <div>
                <label className={labelClass} htmlFor="tx-end">Ends on (optional)</label>
                <input
                  id="tx-end"
                  type="date"
                  value={form.endDate}
                  min={datetime.slice(0, 10)}
                  onChange={(e) => set('endDate', e.target.value)}
                  className={inputClass}
                />
                {fieldError('end_date')}
                <p className="text-[11px] text-slate-500 mt-1.5">
                  The next occurrences are added automatically on their date. A matching charge in an imported
                  CSV replaces the automatic one.
                </p>
              </div>
            )}
          </div>
          )}

          <div>
            <label className={labelClass} htmlFor="tx-notes">Notes</label>
            <input
              id="tx-notes"
              maxLength={1000}
              value={form.notes}
              onChange={(e) => set('notes', e.target.value)}
              className={inputClass}
            />
          </div>

          {error && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs space-y-1">
              <p className="flex items-start gap-2 font-semibold">
                <span className="material-symbols-outlined !text-[16px] shrink-0 text-rose-600">error</span>
                <span>{error}</span>
              </p>
              {fieldErrors.length > 0 && (
                <ul className="font-mono pl-6 space-y-0.5">
                  {fieldErrors.map((e, i) => (
                    <li key={i}>
                      <strong>{e.field}</strong>: {e.message}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={close}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving || !(transaction?.accountId ?? accountId)}
              className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-5 py-2 rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
            >
              <span className="material-symbols-outlined !text-[16px]">{saving ? 'hourglass_top' : 'check'}</span>
              {saving
                ? 'Saving...'
                : isEdit
                  ? 'Save changes'
                  : form.repeat
                    ? 'Add recurring transaction'
                    : 'Add transaction'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
