import React, { useState } from 'react';
import { useAsync } from '../../hooks/useAsync';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import {
  changePassword,
  getRecoveryCodesRemaining,
  regenerateRecoveryCodes,
} from '../../services/security';
import type { ApiFieldError } from '../../types/api';
import { RecoveryCodesDialog } from './RecoveryCodesDialog';

interface SecurityDialogProps {
  onClose: () => void;
  /** Short confirmation in the copilot chat. */
  onSay: (message: string) => void;
}

const inputClass =
  'w-full text-sm px-3 py-2 rounded-xl border border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden bg-white';
const labelClass = 'block text-xs font-semibold text-slate-700 mb-1';

/** Change the password and manage the recovery codes. */
export const SecurityDialog: React.FC<SecurityDialogProps> = ({ onClose, onSay }) => {
  const [version, setVersion] = useState(0);
  const remaining = useAsync(getRecoveryCodesRemaining, `recovery-codes|${version}`);

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [changing, setChanging] = useState(false);
  const [changeError, setChangeError] = useState<string | null>(null);
  const [changeFields, setChangeFields] = useState<ApiFieldError[]>([]);
  const [changed, setChanged] = useState(false);

  const [codesPassword, setCodesPassword] = useState('');
  const [generating, setGenerating] = useState(false);
  const [codesError, setCodesError] = useState<string | null>(null);
  const [newCodes, setNewCodes] = useState<string[] | null>(null);

  const fieldError = (field: string) => {
    const message = changeFields.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const submitChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setChanging(true);
    setChangeError(null);
    setChangeFields([]);
    try {
      await changePassword(current, next);
      setCurrent('');
      setNext('');
      setChanged(true);
      onSay('Password changed. Your other sessions were signed out.');
    } catch (err: unknown) {
      setChangeError(getErrorMessage(err, 'Could not change the password.'));
      setChangeFields(getFieldErrors(err));
    } finally {
      setChanging(false);
    }
  };

  const submitCodes = async (e: React.FormEvent) => {
    e.preventDefault();
    setGenerating(true);
    setCodesError(null);
    try {
      setNewCodes(await regenerateRecoveryCodes(codesPassword));
      setCodesPassword('');
    } catch (err: unknown) {
      setCodesError(
        getFieldErrors(err).some((f) => f.field === 'current_password')
          ? 'The password is incorrect.'
          : getErrorMessage(err, 'Could not generate new codes.'),
      );
    } finally {
      setGenerating(false);
    }
  };

  const left = remaining.data;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150">
      <div role="dialog" aria-modal="true" aria-labelledby="security-title" className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full overflow-hidden flex flex-col max-h-[90vh]">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <h2 id="security-title" className="text-base font-bold text-slate-900">Security</h2>
          <button onClick={onClose} aria-label="Close" className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer">
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        <div className="p-5 space-y-6 overflow-y-auto">
          <section>
            <h3 className="text-sm font-bold text-slate-900">Change password</h3>
            <p className="text-xs text-slate-500 mt-0.5 mb-3">Your other devices will be signed out.</p>
            <form onSubmit={submitChange} className="space-y-3">
              <div>
                <label className={labelClass} htmlFor="security-current">Current password</label>
                <input id="security-current" required type="password" autoComplete="current-password" value={current}
                  onChange={(e) => setCurrent(e.target.value)} className={inputClass} />
                {fieldError('current_password')}
              </div>
              <div>
                <label className={labelClass} htmlFor="security-new">New password</label>
                <input id="security-new" required type="password" minLength={10} maxLength={128} autoComplete="new-password"
                  value={next} onChange={(e) => setNext(e.target.value)} className={inputClass} />
                <p className="text-[11px] text-slate-400 mt-1">At least 10 characters.</p>
                {fieldError('new_password')}
              </div>
              {changeError && changeFields.length === 0 && <p className="text-xs text-rose-600" role="alert">{changeError}</p>}
              {changed && <p className="text-xs text-emerald-700" role="status">Password changed.</p>}
              <button type="submit" disabled={changing}
                className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white px-4 py-2 rounded-xl text-xs font-semibold cursor-pointer">
                {changing ? 'Saving...' : 'Change password'}
              </button>
            </form>
          </section>

          <section className="pt-5 border-t border-slate-100">
            <h3 className="text-sm font-bold text-slate-900">Recovery codes</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              {left === undefined
                ? 'Loading...'
                : `You have ${left} unused ${left === 1 ? 'code' : 'codes'}. They let you reset your password if you forget it.`}
            </p>
            {left !== undefined && left <= 2 && (
              <p className="mt-2 p-2.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
                Running low: generate new codes and keep them somewhere safe.
              </p>
            )}
            <form onSubmit={submitCodes} className="mt-3 flex flex-wrap items-end gap-2">
              <div className="flex-1 min-w-[10rem]">
                <label className={labelClass} htmlFor="security-codes-password">Current password</label>
                <input id="security-codes-password" required type="password" autoComplete="current-password"
                  value={codesPassword} onChange={(e) => setCodesPassword(e.target.value)} className={inputClass} />
              </div>
              <button type="submit" disabled={generating}
                className="px-4 py-2 rounded-xl border border-slate-200 hover:bg-slate-50 disabled:opacity-50 text-xs font-semibold text-slate-700 cursor-pointer">
                {generating ? 'Generating...' : 'Generate new codes'}
              </button>
              {codesError && <p className="w-full text-xs text-rose-600" role="alert">{codesError}</p>}
              <p className="w-full text-[11px] text-slate-400">The old codes stop working.</p>
            </form>
          </section>
        </div>
      </div>

      {newCodes && (
        <RecoveryCodesDialog
          codes={newCodes}
          continueLabel="Done"
          onContinue={() => {
            setNewCodes(null);
            setVersion((v) => v + 1);
            onSay('New recovery codes generated. The old ones no longer work.');
          }}
        />
      )}
    </div>
  );
};
