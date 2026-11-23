import React, { useState } from 'react';
import { exportTransactionsCsv, saveFile } from '../../services/transactions';
import { getErrorMessage } from '../../services/errors';
import type { Account } from '../../types/account';
import { formatLocalDateToISO } from '../../utils/dateHelper';

interface ExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  accounts: Account[];
  /** Preselected account: the one on screen. */
  defaultAccountId: string | null;
}

type Period = 'this-month' | 'last-month' | 'this-year' | 'all' | 'custom';

const PERIODS: { id: Period; label: string }[] = [
  { id: 'this-month', label: 'This month' },
  { id: 'last-month', label: 'Last month' },
  { id: 'this-year', label: 'This year' },
  { id: 'all', label: 'All time' },
  { id: 'custom', label: 'Custom' },
];

const ALL_ACCOUNTS = '';

/** Start and end (inclusive, yyyy-MM-dd) of a preset period; null means no limit. */
const rangeOf = (period: Exclude<Period, 'custom'>): [string | null, string | null] => {
  const today = new Date();
  const y = today.getFullYear();
  const m = today.getMonth();
  switch (period) {
    case 'this-month':
      return [formatLocalDateToISO(new Date(y, m, 1)), formatLocalDateToISO(new Date(y, m + 1, 0))];
    case 'last-month':
      return [formatLocalDateToISO(new Date(y, m - 1, 1)), formatLocalDateToISO(new Date(y, m, 0))];
    case 'this-year':
      return [formatLocalDateToISO(new Date(y, 0, 1)), formatLocalDateToISO(new Date(y, 11, 31))];
    case 'all':
      return [null, null];
  }
};

/** Downloads transactions as CSV (GET /transactions/export): one account or all, for a period. */
export const ExportModal: React.FC<ExportModalProps> = ({ isOpen, onClose, accounts, defaultAccountId }) => {
  const [accountId, setAccountId] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>('this-month');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [isExporting, setIsExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** The last download had no rows: say so instead of saving a file with only the header. */
  const [empty, setEmpty] = useState(false);

  if (!isOpen) return null;

  // Until the user picks one, the account on screen.
  const selectedAccount = accountId ?? defaultAccountId ?? ALL_ACCOUNTS;
  const [startDate, endDate] =
    period === 'custom' ? [customStart || null, customEnd || null] : rangeOf(period);
  const today = formatLocalDateToISO(new Date());
  const invalidRange = startDate !== null && endDate !== null && startDate > endDate;
  // The picker already blocks later days (max); this also catches a date typed by hand.
  const futureDate = period === 'custom' && [customStart, customEnd].some((date) => date > today);

  const close = () => {
    setAccountId(null);
    setPeriod('this-month');
    setCustomStart('');
    setCustomEnd('');
    setError(null);
    setEmpty(false);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (invalidRange || futureDate) return;
    setIsExporting(true);
    setError(null);
    setEmpty(false);
    try {
      const { blob, filename, rows } = await exportTransactionsCsv({
        accountId: selectedAccount === ALL_ACCOUNTS ? null : selectedAccount,
        startDate,
        endDate,
      });
      if (rows === 0) {
        setEmpty(true);
        return;
      }
      saveFile(blob, filename);
      close();
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Failed to export transactions.'));
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="export-title"
        className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full overflow-hidden flex flex-col max-h-[90vh]"
      >
        <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
              <span aria-hidden="true" className="material-symbols-outlined !text-[24px]">download</span>
            </div>
            <div>
              <h2 id="export-title" className="text-lg font-bold text-slate-900 font-sans">Export Transactions</h2>
              <p className="text-xs text-slate-500">CSV in the import format · opens in Excel or Google Sheets</p>
            </div>
          </div>
          <button
            onClick={close}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 transition-colors"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5 overflow-y-auto">
          <div>
            <label
              htmlFor="export-account"
              className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase font-mono tracking-wider"
            >
              Account
            </label>
            <select
              id="export-account"
              value={selectedAccount}
              onChange={(e) => {
                setAccountId(e.target.value);
                setEmpty(false);
              }}
              className="w-full border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-800 bg-white focus:outline-hidden focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
            >
              <option value={ALL_ACCOUNTS}>All accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </div>

          <fieldset>
            <legend className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase font-mono tracking-wider">
              Period
            </legend>
            <div className="flex flex-wrap gap-1.5">
              {PERIODS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  aria-pressed={period === p.id}
                  onClick={() => {
                    setPeriod(p.id);
                    setEmpty(false);
                  }}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
                    period === p.id
                      ? 'bg-emerald-600 border-emerald-600 text-white'
                      : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-emerald-50 hover:border-emerald-300'
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>

            {period === 'custom' && (
              <div className="grid grid-cols-2 gap-3 mt-3">
                <label className="text-xs text-slate-600">
                  From
                  <input
                    type="date"
                    value={customStart}
                    max={customEnd || today}
                    onChange={(e) => {
                      setCustomStart(e.target.value);
                      setEmpty(false);
                    }}
                    className="mt-1 w-full border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-800"
                  />
                </label>
                <label className="text-xs text-slate-600">
                  To
                  <input
                    type="date"
                    value={customEnd}
                    min={customStart || undefined}
                    max={today}
                    onChange={(e) => {
                      setCustomEnd(e.target.value);
                      setEmpty(false);
                    }}
                    className="mt-1 w-full border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-800"
                  />
                </label>
                <p className="col-span-2 text-[11px] text-slate-500">Leave a date empty for no limit.</p>
              </div>
            )}
            {invalidRange && (
              <p role="alert" className="mt-2 text-xs text-rose-600">"From" must be on or before "To".</p>
            )}
            {futureDate && (
              <p role="alert" className="mt-2 text-xs text-rose-600">Dates cannot be after today.</p>
            )}
          </fieldset>

          <p className="text-[11px] text-slate-500 bg-slate-50 border border-slate-200 rounded-xl p-3">
            Same columns as the import. Expenses are negative and income positive, so the Amount column adds up to
            the net balance.
          </p>

          {empty && (
            <p role="status" className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
              No transactions in this period{selectedAccount === ALL_ACCOUNTS ? '' : ' for this account'}. Choose
              another period or account.
            </p>
          )}

          {error && (
            <p role="alert" className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={close}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100 cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isExporting || invalidRange || futureDate}
              className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-5 py-2 rounded-xl text-xs font-semibold shadow-sm cursor-pointer flex items-center gap-1.5"
            >
              <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">download</span>
              {isExporting ? 'Exporting...' : 'Download CSV'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
