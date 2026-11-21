import React, { useState } from 'react';
import { uploadTransactionsCsv } from '../../services/transactions';
import { getErrorMessage, getFieldErrors, getProblem } from '../../services/errors';
import type { ApiFieldError, CsvUploadResponse } from '../../types/api';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Called after a successful import, so the dashboard can reload its data. */
  onImported: (result: CsvUploadResponse) => void;
}

/** Header of the CSV format accepted by POST /transactions/upload-csv (docs/openapi.yaml). */
const COLUMNS = ['Type', 'Date', 'Time', 'Name', 'Amount', 'Category Group', 'Category', 'Account', 'Notes'];

const SAMPLE_CSV = `${COLUMNS.join(',')}
Expense,2026-11-02 13:35:00,13:35,La tiendita,120.50,Food,Groceries,Debit card,
Expense,2026-11-03 09:10:00,09:10,Starbucks,85.00,Drink,Coffee,Debit card,
Expense,2026-11-05 20:00:00,20:00,Cinepolis,240.00,Entertainment,Movies,Debit card,"Movie night, 2 tickets"
Income,2026-11-15 09:00:00,09:00,Employer Inc,-20000.00,Salary,Payroll,Debit card,November salary
Expense,2026-11-18 18:30:00,18:30,Uber,95.40,Travel,Taxi,Credit card,
Expense,2026-11-20 15:58:12,15:58,Engigas,232.50,Utilities,Gas,Debit card,`;

/** Data rows (non-empty lines after the header); only for the preview, the server does the real parsing. */
const countRows = (text: string) => Math.max(0, text.split(/\r?\n/).filter((line) => line.trim()).length - 1);

export const UploadModal: React.FC<UploadModalProps> = ({ isOpen, onClose, onImported }) => {
  const [csvText, setCsvText] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<ApiFieldError[]>([]);
  const [result, setResult] = useState<CsvUploadResponse | null>(null);

  if (!isOpen) return null;

  const resetFeedback = () => {
    setError(null);
    setRowErrors([]);
    setResult(null);
  };

  const close = () => {
    setCsvText('');
    setSelectedFile(null);
    resetFeedback();
    onClose();
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    resetFeedback();
    const file = e.target.files?.[0];
    if (!file) return;
    setSelectedFile(file);
    // Shown in the text area as a preview; the original file is what gets uploaded.
    file.text().then(setCsvText);
  };

  const handleTextChange = (text: string) => {
    setCsvText(text);
    setSelectedFile(null);
    resetFeedback();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!csvText.trim()) return;

    setIsUploading(true);
    resetFeedback();
    try {
      const file = selectedFile ?? new Blob([csvText], { type: 'text/csv' });
      const response = await uploadTransactionsCsv(file, selectedFile?.name ?? 'pasted.csv');
      setResult(response);
      onImported(response);
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Failed to import transactions.'));
      // CSV_INVALID_ROWS lists every problem with its row; VALIDATION_ERROR is about the file itself.
      setRowErrors(getProblem(err)?.code === 'CSV_INVALID_ROWS' ? getFieldErrors(err) : []);
    } finally {
      setIsUploading(false);
    }
  };

  const rows = countRows(csvText);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-2xl w-full overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[24px]">cloud_upload</span>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 font-sans">Import Transactions</h2>
              <p className="text-xs text-slate-500">
                All or nothing · rows already imported are skipped · missing accounts are created
              </p>
            </div>
          </div>
          <button
            onClick={close}
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 transition-colors"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        {result ? (
          /* Success summary */
          <div className="p-6 space-y-4 overflow-y-auto">
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-sm space-y-1">
              <p className="flex items-center gap-2 font-semibold">
                <span className="material-symbols-outlined !text-[18px] text-emerald-600">check_circle</span>
                {result.records_imported} transaction{result.records_imported === 1 ? '' : 's'} imported
              </p>
              {result.records_duplicated > 0 && (
                <p className="text-xs text-emerald-800">
                  {result.records_duplicated} row{result.records_duplicated === 1 ? ' was' : 's were'} already imported and
                  skipped.
                </p>
              )}
              {result.accounts_created.length > 0 && (
                <p className="text-xs text-emerald-800">
                  New account{result.accounts_created.length === 1 ? '' : 's'}:{' '}
                  <strong>{result.accounts_created.map((a) => a.name).join(', ')}</strong>
                </p>
              )}
            </div>
            <div className="flex justify-end">
              <button
                onClick={close}
                className="bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2 rounded-xl text-xs font-semibold shadow-sm cursor-pointer"
              >
                Done
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-y-auto">
            {/* Expected columns */}
            <div className="bg-slate-50 rounded-xl p-3.5 border border-slate-200 text-xs text-slate-700">
              <span className="font-semibold text-slate-900 block mb-1.5">Header (in this order):</span>
              <div className="flex flex-wrap gap-1.5">
                {COLUMNS.map((col) => (
                  <span
                    key={col}
                    className="font-mono bg-white px-2 py-0.5 rounded border border-slate-300 text-slate-800 font-medium"
                  >
                    {col}
                  </span>
                ))}
              </div>
              <p className="mt-2 text-slate-500">
                Date as <span className="font-mono">yyyy-MM-dd HH:mm:ss</span>. Expenses positive, income negative.{' '}
                <span className="font-mono">Category Group</span> must be one of the 26 groups (empty = automatic);{' '}
                <span className="font-mono">Category</span> is free text. UTF-8, up to 1 MB.
              </p>
            </div>

            {/* File input */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase font-mono tracking-wider">
                Choose CSV File
              </label>
              <input
                type="file"
                accept=".csv"
                onChange={handleFileUpload}
                className="block w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-emerald-50 file:text-emerald-700 hover:file:bg-emerald-100 file:cursor-pointer border border-slate-200 rounded-xl p-1 bg-white"
              />
            </div>

            {/* Or paste */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold text-slate-700 uppercase font-mono tracking-wider">
                  Or Paste CSV Data
                </label>
                <button
                  type="button"
                  onClick={() => handleTextChange(SAMPLE_CSV)}
                  className="text-xs text-emerald-600 hover:text-emerald-700 font-medium hover:underline flex items-center gap-1 cursor-pointer"
                >
                  <span className="material-symbols-outlined !text-[14px]">auto_fix_high</span>
                  Load Sample Data
                </button>
              </div>
              <textarea
                rows={6}
                value={csvText}
                onChange={(e) => handleTextChange(e.target.value)}
                placeholder={`${COLUMNS.join(',')}\nExpense,2026-09-25 15:58:12,15:58,Engigas,232.5,Utilities,Gas,Debit card,`}
                className="w-full font-mono text-xs p-3 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-slate-50/50 resize-y"
              />
              {rows > 0 && !error && (
                <p className="text-xs text-slate-500 mt-1">
                  {rows} data row{rows === 1 ? '' : 's'}
                  {selectedFile ? ` in ${selectedFile.name}` : ''}
                </p>
              )}
            </div>

            {/* Errors returned by the API */}
            {error && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs space-y-2">
                <p className="flex items-start gap-2 font-semibold">
                  <span className="material-symbols-outlined !text-[16px] shrink-0 text-rose-600">error</span>
                  <span>{error}</span>
                </p>
                {rowErrors.length > 0 && (
                  <ul className="max-h-40 overflow-y-auto font-mono space-y-0.5 pl-6">
                    {rowErrors.map((e, i) => (
                      <li key={i}>
                        {e.row !== undefined && <span className="text-rose-500">Row {e.row} · </span>}
                        <strong>{e.field}</strong>: {e.message}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {/* Footer */}
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
                disabled={!csvText.trim() || isUploading}
                className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-5 py-2 rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
              >
                <span className="material-symbols-outlined !text-[16px]">
                  {isUploading ? 'hourglass_top' : 'file_upload'}
                </span>
                {isUploading ? 'Importing...' : 'Import'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
