import React from 'react';
import type { Account } from '../../types/account';
import type { AccountSpending } from '../../hooks/useDashboardData';
import { AccountsSection } from './AccountsSection';

export type View = 'dashboard' | 'transactions' | 'goals';

interface SidebarProps {
  view: View;
  onChangeView: (view: View) => void;
  /** Desktop: icons only. */
  collapsed: boolean;
  /** Mobile: drawer open. */
  mobileOpen: boolean;
  onCloseMobile: () => void;
  accounts: Account[];
  activeAccountId: string | null;
  spending: Record<string, AccountSpending> | undefined;
  loadingAccounts: boolean;
  onSelectAccount: (accountId: string) => void;
  onEditAccount: (account: Account) => void;
  onCreateAccount: () => void;
  /** Goals behind or overdue: a badge on the Goals item (the dashboard stays as it is). */
  goalsNeedingAttention: number;
}

const NAV: { id: View; label: string; icon: string }[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'space_dashboard' },
  { id: 'transactions', label: 'Transactions', icon: 'receipt_long' },
  { id: 'goals', label: 'Goals', icon: 'flag' },
];

export const Sidebar: React.FC<SidebarProps> = ({
  view,
  onChangeView,
  collapsed,
  mobileOpen,
  onCloseMobile,
  accounts,
  activeAccountId,
  spending,
  loadingAccounts,
  onSelectAccount,
  onEditAccount,
  onCreateAccount,
  goalsNeedingAttention,
}) => {
  // In the drawer everything is shown; on desktop the collapsed bar shows icons only.
  const compact = collapsed && !mobileOpen;

  const nav = (
    <nav className="space-y-1">
      {NAV.map((item) => {
        const active = view === item.id;
        const badge = item.id === 'goals' && goalsNeedingAttention > 0 ? goalsNeedingAttention : 0;
        const badgeLabel = `${badge} goal${badge === 1 ? '' : 's'} need${badge === 1 ? 's' : ''} attention`;
        return (
          <button
            key={item.id}
            onClick={() => {
              onChangeView(item.id);
              onCloseMobile();
            }}
            title={badge ? `${item.label} · ${badgeLabel}` : item.label}
            aria-label={badge ? `${item.label}, ${badgeLabel}` : undefined}
            className={`relative w-full flex items-center gap-3 rounded-xl text-sm font-semibold transition-all cursor-pointer ${
              compact ? 'justify-center p-2.5' : 'px-3 py-2.5'
            } ${active ? 'bg-emerald-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'}`}
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[20px]">{item.icon}</span>
            {!compact && item.label}
            {badge > 0 &&
              (compact ? (
                <span className="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-amber-500 text-white text-[10px] font-bold leading-4 text-center">
                  {badge}
                </span>
              ) : (
                <span
                  className={`ml-auto min-w-5 h-5 px-1.5 rounded-full text-[11px] font-bold leading-5 text-center ${
                    active ? 'bg-white text-amber-700' : 'bg-amber-100 text-amber-800'
                  }`}
                >
                  {badge}
                </span>
              ))}
          </button>
        );
      })}
    </nav>
  );

  const accountsBlock = compact ? (
    <ul className="space-y-1.5">
      {accounts.map((account) => (
        <li key={account.id}>
          <button
            onClick={() => onSelectAccount(account.id)}
            title={account.name}
            className={`w-full aspect-square rounded-xl text-sm font-bold flex items-center justify-center cursor-pointer ${
              account.id === activeAccountId
                ? 'bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200'
                : 'text-slate-500 hover:bg-slate-100'
            }`}
          >
            {account.name.charAt(0).toUpperCase()}
          </button>
        </li>
      ))}
    </ul>
  ) : (
    <AccountsSection
      accounts={accounts}
      activeAccountId={activeAccountId}
      spending={spending}
      loading={loadingAccounts}
      onSelect={(id) => {
        onSelectAccount(id);
        onCloseMobile();
      }}
      onEdit={onEditAccount}
      onCreate={onCreateAccount}
    />
  );

  return (
    <>
      {/* Mobile backdrop */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-xs lg:hidden" onClick={onCloseMobile} />
      )}
      <aside
        className={`bg-white border-r border-slate-200 flex flex-col gap-6 overflow-y-auto p-3
          fixed inset-y-0 left-0 z-50 w-72 transition-transform duration-200 ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}
          lg:sticky lg:top-[65px] lg:z-10 lg:h-[calc(100vh-65px)] lg:translate-x-0 lg:shrink-0 ${compact ? 'lg:w-16' : 'lg:w-64'}`}
      >
        <div className="flex items-center justify-between lg:hidden px-1">
          <p className="text-sm font-bold text-slate-900">Menu</p>
          <button onClick={onCloseMobile} aria-label="Close menu" className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 cursor-pointer">
            <span aria-hidden="true" className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>
        {nav}
        <div className="border-t border-slate-100" />
        {accountsBlock}
      </aside>
    </>
  );
};
