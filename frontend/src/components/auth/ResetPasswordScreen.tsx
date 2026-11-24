import React, { useState } from 'react';
import { resetPassword } from '../../auth/services';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError } from '../../types/api';
import { AuthCard } from './AuthCard';
import { authButtonClass, authInputClass, authLabelClass } from './authStyles';

interface ResetPasswordScreenProps {
  onBackToLogin: () => void;
}

/** Forgot password: one of the recovery codes shown at sign-up proves the account. */
export const ResetPasswordScreen: React.FC<ResetPasswordScreenProps> = ({ onBackToLogin }) => {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [remaining, setRemaining] = useState<number | null>(null);

  const fieldError = (field: string) => {
    const message = fieldErrors.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    try {
      const result = await resetPassword({ email: email.trim(), recovery_code: code, new_password: password });
      setRemaining(result.recovery_codes_remaining);
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Could not reset the password.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  const back = (
    <button type="button" onClick={onBackToLogin} className="font-semibold text-emerald-600 hover:text-emerald-700 cursor-pointer">
      Back to sign in
    </button>
  );

  if (remaining !== null) {
    return (
      <AuthCard icon="check_circle" title="Password changed" subtitle="Sign in with your new password." footer={back}>
        {remaining <= 2 && (
          <p className="mb-5 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
            You have {remaining} recovery {remaining === 1 ? 'code' : 'codes'} left. After signing in, generate new ones
            in Security.
          </p>
        )}
        <button type="button" onClick={onBackToLogin} className={authButtonClass}>
          Sign in
        </button>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      icon="key"
      title="Reset your password"
      subtitle="Use one of the recovery codes you saved when you created your account."
      footer={back}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className={authLabelClass} htmlFor="reset-email">Email address</label>
          <input
            id="reset-email"
            required
            autoFocus
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            className={authInputClass}
          />
        </div>
        <div>
          <label className={authLabelClass} htmlFor="reset-code">Recovery code</label>
          <input
            id="reset-code"
            required
            maxLength={64}
            autoComplete="one-time-code"
            spellCheck={false}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="k7m2-9xqp-h3vt"
            className={`${authInputClass} font-mono tracking-wider`}
          />
        </div>
        <div>
          <label className={authLabelClass} htmlFor="reset-password">New password</label>
          <input
            id="reset-password"
            required
            type="password"
            minLength={10}
            maxLength={128}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={authInputClass}
          />
          <p className="text-[11px] text-slate-400 mt-1">At least 10 characters.</p>
          {fieldError('new_password')}
        </div>
        {error && (
          <p className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold" role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={saving} className={authButtonClass}>
          {saving ? 'Saving...' : 'Set new password'}
        </button>
        <p className="text-[11px] text-slate-400 text-center">
          Lost your codes too? Ask the administrator to reset your password.
        </p>
      </form>
    </AuthCard>
  );
};
