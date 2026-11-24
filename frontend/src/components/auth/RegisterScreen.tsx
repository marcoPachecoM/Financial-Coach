import React, { useState } from 'react';
import { register } from '../../auth/services';
import { useAuth } from '../../contexts/AuthContext';
import { getErrorMessage, getFieldErrors } from '../../services/errors';
import type { ApiFieldError } from '../../types/api';
import type { RegisterResponse } from '../../types/auth';
import { AuthCard } from './AuthCard';
import { authButtonClass, authInputClass, authLabelClass } from './authStyles';
import { RecoveryCodesDialog } from './RecoveryCodesDialog';

interface RegisterScreenProps {
  onBackToLogin: () => void;
}

/** Same rule as the API (PasswordPolicy): length matters most. */
const MIN_PASSWORD = 10;

/** Sign-up. After it, the recovery codes are shown once, and only then the app opens. */
export const RegisterScreen: React.FC<RegisterScreenProps> = ({ onBackToLogin }) => {
  const { signInWithTokens } = useAuth();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [registered, setRegistered] = useState<RegisterResponse | null>(null);

  const fieldError = (field: string) => {
    const message = fieldErrors.find((e) => e.field === field)?.message;
    return message ? <p className="text-[11px] text-rose-600 mt-1">{message}</p> : null;
  };
  const mismatch = confirm.length > 0 && confirm !== password;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (mismatch) return;
    setSaving(true);
    setError(null);
    setFieldErrors([]);
    try {
      setRegistered(await register({ username: username.trim(), email: email.trim(), password }));
    } catch (err: unknown) {
      // 409: the email or username is taken; 429: too many sign-ups from this network.
      setError(getErrorMessage(err, 'Could not create the account.'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <AuthCard
        icon="person_add"
        title="Create your account"
        subtitle="Import your bank statements and see where your money goes."
        footer={
          <>
            Already have an account?{' '}
            <button type="button" onClick={onBackToLogin} className="font-semibold text-emerald-600 hover:text-emerald-700 cursor-pointer">
              Sign in
            </button>
          </>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={authLabelClass} htmlFor="register-username">Username</label>
            <input
              id="register-username"
              required
              autoFocus
              minLength={3}
              maxLength={64}
              pattern="[A-Za-z0-9._\-]+"
              title="Letters, digits, '.', '_' and '-'"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="ana.lopez"
              className={authInputClass}
            />
            {fieldError('username')}
          </div>
          <div>
            <label className={authLabelClass} htmlFor="register-email">Email address</label>
            <input
              id="register-email"
              required
              type="email"
              maxLength={255}
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className={authInputClass}
            />
            {fieldError('email')}
          </div>
          <div>
            <label className={authLabelClass} htmlFor="register-password">Password</label>
            <input
              id="register-password"
              required
              type="password"
              minLength={MIN_PASSWORD}
              maxLength={128}
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={authInputClass}
            />
            <p className={`text-[11px] mt-1 ${password.length >= MIN_PASSWORD ? 'text-emerald-600' : 'text-slate-400'}`}>
              At least {MIN_PASSWORD} characters. A few words make a strong, easy-to-remember password.
            </p>
            {fieldError('password')}
          </div>
          <div>
            <label className={authLabelClass} htmlFor="register-confirm">Confirm password</label>
            <input
              id="register-confirm"
              required
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              aria-invalid={mismatch}
              className={authInputClass}
            />
            {mismatch && <p className="text-[11px] text-rose-600 mt-1">The passwords don't match.</p>}
          </div>
          {error && (
            <p className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold" role="alert">
              {error}
            </p>
          )}
          <button type="submit" disabled={saving || mismatch} className={authButtonClass}>
            {saving ? 'Creating account...' : 'Create account'}
          </button>
        </form>
      </AuthCard>

      {registered && (
        <RecoveryCodesDialog
          codes={registered.recovery_codes}
          continueLabel="Go to my dashboard"
          onContinue={() => signInWithTokens(registered.access_token, registered.refresh_token, false)}
        />
      )}
    </>
  );
};
