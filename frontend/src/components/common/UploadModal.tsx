import React, { useState } from 'react';
import type { Transaction } from '../../types/transaction';
import { uploadTransactionsCsv } from '../../services/transactions';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  onUploadTransactions: (
    newTransactions: Transaction[],
    replaceExisting: boolean,
    newConnectionId?: number | null,
  ) => void;
}

const SAMPLE_CSV = `id,date,provider_name,amount,mcc
tx-2001,2025-05-12,Costco Wholesale,-189.50,5411
tx-2002,2025-05-12,HBO Max,-16.99,4899
tx-2003,2025-05-11,Lyft Ride,-18.25,4121
tx-2004,2025-05-10,Chipotle Mexican Grill,-14.80,5814
tx-2005,2025-05-09,Chevron Gas,-42.00,5541
tx-2006,2025-05-09,GitHub Copilot,-10.00,5734`;

export const UploadModal: React.FC<UploadModalProps> = ({
  isOpen,
  onClose,
  onUploadTransactions,
}) => {
  const [csvText, setCsvText] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [parsedCount, setParsedCount] = useState<number | null>(null);
  const [replaceExisting, setReplaceExisting] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  if (!isOpen) return null;

  const parseCSV = (content: string): Transaction[] => {
    const lines = content.trim().split('\n').map((l) => l.trim()).filter(Boolean);
    if (lines.length < 2) {
      throw new Error('CSV must contain a header row and at least one data row.');
    }

    const header = lines[0].split(',').map((h) => h.trim().toLowerCase().replace(/['"]/g, ''));
    const requiredCols = ['id', 'date', 'provider_name', 'amount', 'mcc'];
    
    // Check missing columns
    for (const col of requiredCols) {
      if (!header.includes(col)) {
        throw new Error(`Missing required column: "${col}". Required columns are: ${requiredCols.join(', ')}`);
      }
    }

    const idIdx = header.indexOf('id');
    const dateIdx = header.indexOf('date');
    const providerIdx = header.indexOf('provider_name');
    const amountIdx = header.indexOf('amount');
    const mccIdx = header.indexOf('mcc');

    const transactions: Transaction[] = [];

    for (let i = 1; i < lines.length; i++) {
      // Split on comma taking into account possible quoted strings
      const parts = lines[i].split(',').map((p) => p.trim().replace(/^["']|["']$/g, ''));
      if (parts.length < header.length) continue;

      const rawAmount = parts[amountIdx];
      const parsedAmount = parseFloat(rawAmount);
      if (isNaN(parsedAmount)) {
        throw new Error(`Row ${i + 1}: Invalid numeric amount "${rawAmount}"`);
      }

      transactions.push({
        id: parts[idIdx] || `tx-gen-${Date.now()}-${i}`,
        date: parts[dateIdx] || new Date().toISOString().split('T')[0],
        provider_name: parts[providerIdx] || 'Unknown Merchant',
        amount: parsedAmount,
        mcc: parts[mccIdx] || '0000',
        status: 'ai_approved',
      });
    }

    if (transactions.length === 0) {
      throw new Error('No valid transactions could be parsed from the provided data.');
    }

    return transactions;
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    setError(null);
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedFile(file);
    const reader = new FileReader();
    reader.onload = (evt) => {
      const text = evt.target?.result as string;
      setCsvText(text);
      try {
        const parsed = parseCSV(text);
        setParsedCount(parsed.length);
        setError(null);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Error parsing CSV file');
        setParsedCount(null);
      }
    };
    reader.readAsText(file);
  };

  const handleTextChange = (text: string) => {
    setCsvText(text);
    setSelectedFile(null);
    setError(null);
    if (!text.trim()) {
      setParsedCount(null);
      return;
    }
    try {
      const parsed = parseCSV(text);
      setParsedCount(parsed.length);
    } catch {
      setParsedCount(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!csvText.trim()) return;

    setIsUploading(true);
    setError(null);

    try {
      const parsed = parseCSV(csvText);
      const fileToUpload = selectedFile || new Blob([csvText], { type: 'text/csv' });
      const filename = selectedFile?.name || 'transactions.csv';

      let newConnectionId: number | null = null;
      try {
        const response = await uploadTransactionsCsv(fileToUpload, filename);
        newConnectionId = response.connection_id;
      } catch (uploadErr) {
        console.warn('Backend upload-csv warning, using client parsed data:', uploadErr);
      }

      onUploadTransactions(parsed, replaceExisting, newConnectionId);
      onClose();
      setCsvText('');
      setSelectedFile(null);
      setParsedCount(null);
      setError(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to import transactions');
    } finally {
      setIsUploading(false);
    }
  };

  const loadSample = () => {
    handleTextChange(SAMPLE_CSV);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-xl w-full overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[24px]">cloud_upload</span>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 font-sans">Upload Transaction Dataset</h2>
              <p className="text-xs text-slate-500 font-mono">
                Schema: id, date, provider_name, amount, mcc
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 transition-colors"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        {/* Modal Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-y-auto">
          {/* Expected Columns Badge info */}
          <div className="bg-slate-50 rounded-xl p-3.5 border border-slate-200 text-xs text-slate-700">
            <span className="font-semibold text-slate-900 block mb-1.5">
              Required CSV Columns:
            </span>
            <div className="flex flex-wrap gap-1.5">
              {['id', 'date', 'provider_name', 'amount', 'mcc'].map((col) => (
                <span
                  key={col}
                  className="font-mono bg-white px-2 py-0.5 rounded border border-slate-300 text-slate-800 font-medium"
                >
                  {col}
                </span>
              ))}
            </div>
          </div>

          {/* File Input Box */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5 uppercase font-mono tracking-wider">
              Choose CSV File
            </label>
            <input
              type="file"
              accept=".csv,.txt"
              onChange={handleFileUpload}
              className="block w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-emerald-50 file:text-emerald-700 hover:file:bg-emerald-100 file:cursor-pointer border border-slate-200 rounded-xl p-1 bg-white"
            />
          </div>

          {/* Or Paste CSV Area */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-semibold text-slate-700 uppercase font-mono tracking-wider">
                Or Paste CSV Data
              </label>
              <button
                type="button"
                onClick={loadSample}
                className="text-xs text-emerald-600 hover:text-emerald-700 font-medium hover:underline flex items-center gap-1 cursor-pointer"
              >
                <span className="material-symbols-outlined !text-[14px]">auto_fix_high</span>
                Load Sample Data
              </button>
            </div>
            <textarea
              rows={5}
              value={csvText}
              onChange={(e) => handleTextChange(e.target.value)}
              placeholder="id,date,provider_name,amount,mcc&#10;tx-2001,2025-05-12,Costco,-189.50,5411"
              className="w-full font-mono text-xs p-3 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-slate-50/50 resize-y"
            />
          </div>

          {/* Replace vs Append toggle */}
          <div className="flex items-center gap-2 pt-1">
            <input
              type="checkbox"
              id="replaceExisting"
              checked={replaceExisting}
              onChange={(e) => setReplaceExisting(e.target.checked)}
              className="rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 w-4 h-4 cursor-pointer"
            />
            <label htmlFor="replaceExisting" className="text-xs text-slate-600 cursor-pointer select-none">
              Replace current transaction list (uncheck to merge)
            </label>
          </div>

          {/* Parse Status / Feedback */}
          {error && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
              <span className="material-symbols-outlined !text-[16px] mt-0.5 shrink-0 text-rose-600">error</span>
              <span>{error}</span>
            </div>
          )}

          {parsedCount !== null && !error && (
            <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2">
              <span className="material-symbols-outlined !text-[16px] text-emerald-600">check_circle</span>
              <span>
                Ready to import <strong>{parsedCount}</strong> transaction{parsedCount === 1 ? '' : 's'}.
              </span>
            </div>
          )}

          {/* Footer Buttons */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!csvText.trim() || !!error || isUploading}
              className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-5 py-2 rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
            >
              {isUploading ? (
                <>
                  <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  <span>Importing & Syncing...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined !text-[16px]">file_upload</span>
                  Import Dataset
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
