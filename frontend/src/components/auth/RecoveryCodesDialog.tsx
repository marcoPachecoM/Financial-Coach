import React, { useState } from 'react';

interface RecoveryCodesDialogProps {
  codes: string[];
  /** Enabled only after "I saved them" is checked. */
  onContinue: () => void;
  continueLabel?: string;
}

/**
 * Shows the recovery codes once (after sign-up or when new ones are generated). They are the
 * only way back in without email, so continuing requires confirming they were saved.
 */
export const RecoveryCodesDialog: React.FC<RecoveryCodesDialogProps> = ({
  codes,
  onContinue,
  continueLabel = 'Continue',
}) => {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = codes.join('\n');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const download = () => {
    const blob = new Blob(
      [`Recovery codes (each works once). Keep them somewhere safe.\n\n${text}\n`],
      { type: 'text/plain' },
    );
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'recovery-codes.txt';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="recovery-codes-title"
        className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full p-6"
      >
        <div className="w-11 h-11 rounded-xl bg-amber-50 flex items-center justify-center mb-4">
          <span aria-hidden="true" className="material-symbols-outlined !text-[22px] text-amber-600">key</span>
        </div>
        <h2 id="recovery-codes-title" className="text-lg font-bold text-slate-900">Save your recovery codes</h2>
        <p className="text-sm text-slate-600 mt-1">
          If you forget your password, one of these codes lets you set a new one. Each code works once.{' '}
          <strong className="text-slate-800">We can't show them again.</strong>
        </p>

        <ul className="mt-5 grid grid-cols-2 gap-2 p-4 rounded-xl bg-slate-50 border border-slate-200 font-mono text-sm text-slate-800">
          {codes.map((code) => (
            <li key={code} className="text-center tracking-wider select-all">{code}</li>
          ))}
        </ul>

        <div className="mt-3 flex gap-2">
          <button
            type="button"
            onClick={copy}
            className="flex-1 inline-flex items-center justify-center gap-1.5 h-9 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-semibold text-slate-700 cursor-pointer"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">{copied ? 'check' : 'content_copy'}</span>
            {copied ? 'Copied' : 'Copy'}
          </button>
          <button
            type="button"
            onClick={download}
            className="flex-1 inline-flex items-center justify-center gap-1.5 h-9 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-semibold text-slate-700 cursor-pointer"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">download</span>
            Download .txt
          </button>
        </div>

        <label className="mt-5 flex items-start gap-2.5 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={saved}
            onChange={(e) => setSaved(e.target.checked)}
            className="mt-0.5 w-4 h-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500/20"
          />
          <span className="text-sm text-slate-700">I saved my recovery codes somewhere safe.</span>
        </label>

        <button
          type="button"
          disabled={!saved}
          onClick={onContinue}
          className="mt-5 w-full h-11 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold cursor-pointer"
        >
          {continueLabel}
        </button>
      </div>
    </div>
  );
};
