import React, { useState } from "react";
import { useAuth } from "../contexts/AuthContext";
import { getErrorMessage } from "../services/errors";

interface LoginScreenProps {
  onLogin?: () => void;
  onForgotPassword?: () => void;
  onCreateAccount?: () => void;
}
/** Demo user loaded by the backend's dev profile. Prefilled only in development builds. */
const DEMO_CREDENTIALS = import.meta.env.DEV
  ? { email: "client1@example.local", password: "ClientPass123!" }
  : { email: "", password: "" };

export const LoginScreen: React.FC<LoginScreenProps> = ({
  onLogin,
  onForgotPassword,
  onCreateAccount,
}) => {
  const { login, isLoading } = useAuth();
  const [email, setEmail] = useState<string>(DEMO_CREDENTIALS.email);
  const [password, setPassword] = useState<string>(DEMO_CREDENTIALS.password);
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    try {
      await login(email, password, rememberMe);

      onLogin?.();
    } catch (error) {
      // e.g. "Invalid email or password." or the rate-limit message from the API.
      setError(getErrorMessage(error, "An error occurred during login."));
    }
  };

  return (
    <div className="min-h-screen bg-[#f7f9fb] text-slate-900 font-sans flex items-center justify-center p-4 md:p-6">
      <div className="w-full max-w-[1200px] min-h-[720px] bg-white border border-slate-200 rounded-[28px] shadow-[0_20px_70px_rgba(15,23,42,0.08)] overflow-hidden flex">
        {/* Left / Brand panel */}
        <section className="hidden lg:flex lg:w-[52%] relative overflow-hidden bg-slate-950 p-12 xl:p-16 flex-col justify-between">
          {/* Background decoration */}
          <div className="absolute inset-0 overflow-hidden pointer-events-none">
            <div className="absolute -top-40 -left-40 w-[520px] h-[520px] rounded-full bg-emerald-500/10 blur-3xl" />
            <div className="absolute -bottom-40 -right-20 w-[500px] h-[500px] rounded-full bg-emerald-400/10 blur-3xl" />

            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] border border-white/[0.04] rounded-full" />
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[430px] h-[430px] border border-white/[0.04] rounded-full" />
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[260px] h-[260px] border border-white/[0.04] rounded-full" />
          </div>

          {/* Logo */}
          <div className="relative z-10 flex items-center gap-3">
            <div className="w-11 h-11 rounded-xl bg-emerald-500 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <span className="material-symbols-outlined !text-[25px] text-white">
                account_balance_wallet
              </span>
            </div>

            <div>
              <p className="text-white font-bold text-xl tracking-tight">
                Mush
              </p>
              <p className="text-slate-400 text-[10px] font-mono uppercase tracking-[0.18em]">
                Your financial intelligence
              </p>
            </div>
          </div>

          {/* Main message */}
          <div className="relative z-10 max-w-[500px]">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-emerald-400/20 bg-emerald-400/10 text-emerald-300 text-xs font-semibold mb-7">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              Your finances, understood.
            </div>

            <h1 className="text-4xl xl:text-5xl font-bold tracking-tight text-white leading-[1.08]">
              Your money.
              <br />
              <span className="text-emerald-400">Your intelligence.</span>
            </h1>

            <p className="mt-6 text-slate-400 text-base leading-relaxed max-w-[430px]">
              See where your money goes, understand your spending patterns, and
              make smarter financial decisions with AI-powered insights.
            </p>

            {/* Mini dashboard preview */}
            <div className="mt-10 relative">
              <div className="bg-white/[0.06] border border-white/[0.08] rounded-2xl p-5 backdrop-blur-sm">
                <div className="flex items-center justify-between mb-5">
                  <div>
                    <p className="text-[10px] uppercase tracking-wider text-slate-500 font-mono">
                      Total balance
                    </p>
                    <p className="text-2xl font-bold text-white mt-1">
                      $45,230.50
                    </p>
                  </div>

                  <div className="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
                    <span className="material-symbols-outlined !text-[19px] text-emerald-400">
                      trending_up
                    </span>
                  </div>
                </div>

                {/* Fake chart */}
                <div className="h-20 flex items-end gap-1.5">
                  {[35, 48, 42, 57, 51, 68, 61, 73, 65, 78, 72, 88, 82, 94].map(
                    (height, index) => (
                      <div
                        key={index}
                        className="flex-1 rounded-t-sm bg-emerald-400/60"
                        style={{ height: `${height}%` }}
                      />
                    ),
                  )}
                </div>

                <div className="flex items-center justify-between mt-4 pt-4 border-t border-white/[0.06]">
                  <span className="text-xs text-slate-500">Savings rate</span>
                  <span className="text-xs font-semibold text-emerald-400">
                    +19.8%
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="relative z-10 flex items-center gap-2 text-xs text-slate-500">
            <span className="material-symbols-outlined !text-[15px]">
              verified_user
            </span>
            <span>Your financial data stays secure and private.</span>
          </div>
        </section>

        {/* Right / Login */}
        <section className="w-full lg:w-[48%] flex items-center justify-center p-6 sm:p-10 md:p-14 xl:p-20">
          <div className="w-full max-w-[410px]">
            {/* Mobile logo */}
            <div className="lg:hidden flex items-center gap-3 mb-12">
              <div className="w-10 h-10 rounded-xl bg-emerald-500 flex items-center justify-center">
                <span className="material-symbols-outlined !text-[23px] text-white">
                  account_balance_wallet
                </span>
              </div>

              <div>
                <p className="font-bold text-lg tracking-tight">FinSight</p>
                <p className="text-[9px] text-slate-400 font-mono uppercase tracking-[0.15em]">
                  Financial Intelligence
                </p>
              </div>
            </div>

            {/* Heading */}
            <div className="mb-9">
              <div className="w-12 h-12 rounded-2xl bg-emerald-50 flex items-center justify-center mb-6">
                <span className="material-symbols-outlined !text-[23px] text-emerald-600">
                  lock_open
                </span>
              </div>

              <h2 className="text-3xl font-bold tracking-tight text-slate-900">
                Welcome back
              </h2>

              <p className="text-sm text-slate-500 mt-2">
                Sign in to continue managing your finances.
              </p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Email */}
              <div>
                <label
                  htmlFor="email"
                  className="block text-xs font-semibold text-slate-700 mb-2"
                >
                  Email address
                </label>

                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 !text-[19px] text-slate-400 pointer-events-none">
                    mail
                  </span>

                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                    required
                    className="w-full h-12 pl-11 pr-4 rounded-xl border border-slate-200 bg-slate-50/50 text-sm text-slate-900 placeholder:text-slate-400 outline-none transition-all focus:bg-white focus:border-emerald-500 focus:ring-4 focus:ring-emerald-500/10"
                  />
                </div>
              </div>

              {/* Password */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label
                    htmlFor="password"
                    className="text-xs font-semibold text-slate-700"
                  >
                    Password
                  </label>

                  <button
                    type="button"
                    onClick={onForgotPassword}
                    className="text-xs font-semibold text-emerald-600 hover:text-emerald-700 transition-colors"
                  >
                    Forgot password?
                  </button>
                </div>

                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 !text-[19px] text-slate-400 pointer-events-none">
                    lock
                  </span>

                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    required
                    className="w-full h-12 pl-11 pr-12 rounded-xl border border-slate-200 bg-slate-50/50 text-sm text-slate-900 placeholder:text-slate-400 outline-none transition-all focus:bg-white focus:border-emerald-500 focus:ring-4 focus:ring-emerald-500/10"
                  />

                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                    aria-label={
                      showPassword ? "Hide password" : "Show password"
                    }
                  >
                    <span className="material-symbols-outlined !text-[19px]">
                      {showPassword ? "visibility_off" : "visibility"}
                    </span>
                  </button>
                </div>
              </div>

              {/* Remember */}
              <label className="flex items-center gap-2.5 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                  className="w-4 h-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500/20"
                />

                <span className="text-xs text-slate-500">
                  Keep me signed in
                </span>
              </label>

              {/* Submit */}
              {error && (
                <div className="flex items-start gap-3 p-3.5 rounded-xl border border-red-200 bg-red-50 text-red-700">
                  <span className="material-symbols-outlined !text-[18px] mt-0.5">
                    error
                  </span>

                  <div>
                    <p className="text-xs font-semibold">Unable to sign in</p>

                    <p className="text-xs text-red-600 mt-0.5">{error}</p>
                  </div>
                </div>
              )}

              <button
                type="submit"
                className="w-full h-12 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white text-sm font-semibold transition-all shadow-lg shadow-emerald-600/20 hover:shadow-emerald-600/30 flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <svg
                    className="animate-spin w-5 h-5 text-white"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    ></circle>
                    <path
                      className="opacity-75"
                      d="M12 2.5C6.16 2.5 2.5 6.16 2.5 12S6.16 21.5 12 21.5 21.5 17.84 21.5 12 17.84 2.5 12 2.5Z"
                    ></path>
                  </svg>
                ) : (
                  <>
                    <span>Sign in</span>

                    <span className="material-symbols-outlined !text-[18px]">
                      arrow_forward
                    </span>
                  </>
                )}
              </button>
            </form>

            {/* Divider */}
            <div className="flex items-center gap-4 my-7">
              <div className="h-px flex-1 bg-slate-100" />
              <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-400">
                or continue with
              </span>
              <div className="h-px flex-1 bg-slate-100" />
            </div>

            {/* Google */}
            <button
              type="button"
              className="w-full h-12 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-sm font-semibold text-slate-700 transition-all flex items-center justify-center gap-3"
            >
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  fill="#4285F4"
                  d="M21.35 12.23c0-.72-.06-1.41-.18-2.08H12v3.94h5.22a4.47 4.47 0 0 1-1.94 2.93v2.44h3.14c1.84-1.69 2.93-4.18 2.93-7.23Z"
                />
                <path
                  fill="#34A853"
                  d="M12 21.69c2.63 0 4.84-.87 6.45-2.36l-3.14-2.44c-.87.58-1.98.92-3.31.92-2.54 0-4.69-1.72-5.46-4.03H3.3v2.52A9.75 9.75 0 0 0 12 21.69Z"
                />
                <path
                  fill="#FBBC05"
                  d="M6.54 13.78A5.86 5.86 0 0 1 6.23 12c0-.62.11-1.22.31-1.78V7.7H3.3A9.76 9.76 0 0 0 2.25 12c0 1.57.38 3.05 1.05 4.3l3.24-2.52Z"
                />
                <path
                  fill="#EA4335"
                  d="M12 6.19c1.43 0 2.71.49 3.72 1.46l2.79-2.79C16.84 3.29 14.63 2.31 12 2.31A9.75 9.75 0 0 0 3.3 7.7l3.24 2.52C7.31 7.91 9.46 6.19 12 6.19Z"
                />
              </svg>
              Continue with Google
            </button>

            {/* Register */}
            <p className="text-center text-sm text-slate-500 mt-8">
              Don't have an account?{" "}
              <button
                type="button"
                onClick={onCreateAccount}
                className="font-semibold text-emerald-600 hover:text-emerald-700 transition-colors"
              >
                Create one
              </button>
            </p>

            {/* Bottom security */}
            <div className="flex items-center justify-center gap-1.5 mt-10 text-[10px] text-slate-400">
              <span className="material-symbols-outlined !text-[13px]">
                shield
              </span>
              Secured with industry-standard encryption
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
