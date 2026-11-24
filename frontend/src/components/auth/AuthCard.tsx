import React from 'react';

interface AuthCardProps {
  icon: string;
  title: string;
  subtitle: string;
  children: React.ReactNode;
  /** "Back to sign in" and similar links under the card. */
  footer?: React.ReactNode;
}

/** Centered card shared by the sign-up and password-reset screens. */
export const AuthCard: React.FC<AuthCardProps> = ({ icon, title, subtitle, children, footer }) => (
  <div className="min-h-screen bg-[#f7f9fb] text-slate-900 font-sans flex items-center justify-center p-4">
    <div className="w-full max-w-[440px]">
      <div className="bg-white border border-slate-200 rounded-[24px] shadow-[0_20px_70px_rgba(15,23,42,0.08)] p-6 sm:p-9">
        <div className="w-12 h-12 rounded-2xl bg-emerald-50 flex items-center justify-center mb-5">
          <span aria-hidden="true" className="material-symbols-outlined !text-[23px] text-emerald-600">{icon}</span>
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
        <p className="text-sm text-slate-500 mt-1.5 mb-7">{subtitle}</p>
        {children}
      </div>
      {footer && <div className="text-center text-sm text-slate-500 mt-6">{footer}</div>}
    </div>
  </div>
);
