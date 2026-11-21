import React from 'react';
import type { Account } from '../../types/account';
import type { BudgetStatus } from '../../types/api';
import type { AccountSpending } from '../../hooks/useDashboardData';
import { formatCurrency } from '../../utils/formatters';

interface AccountsSectionProps {
  accounts: Account[];
  activeAccountId: string | null;
  spending: Record<string, AccountSpending> | undefined;
  loading: boolean;
  onSelect: (accountId: string) => void;
  onEdit: (account: Account) => void;
  onCreate: () => void;
}

const BAR: Record<BudgetStatus, string> = {
  ON_TRACK: 'bg-emerald-500',
  WARNING: 'bg-amber-500',
  EXCEEDED: 'bg-rose-500',
  NO_BUDGET: 'bg-slate-300',
};

/** "+12% vs last month": more spending is red, less is green. */
const Comparison: React.FC<{ current: number; previous: number }> = ({ current, previous }) => {
  if (previous === 0) {
    return <span className="text-slate-400">{current > 0 ? 'nothing spent last month' : 'no spending yet'}</span>;
  }
  const change = ((current - previous) / previous) * 100;
  const up = change > 0;
  return (
    <span className={up ? 'text-rose-600' : 'text-emerald-600'}>
      {up ? '▲' : '▼'} {Math.abs(change).toFixed(0)}% vs {formatCurrency(previous)} last month
    </span>
  );
};

/** The account list is also the account selector. */
export const AccountsSection: React.FC<AccountsSectionProps> = ({
  accounts,
  activeAccountId,
  spending,
  loading,
  onSelect,
  onEdit,
  onCreate,
}) => (
  <div>
    <div className="flex items-center justify-between px-2 mb-2">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 font-mono">Accounts</p>
      <button
        onClick={onCreate}
        title="New account"
        className="p-1 rounded-lg text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 cursor-pointer"
      >
        <span className="material-symbols-outlined !text-[18px]">add</span>
      </button>
    </div>

    {accounts.length === 0 && (
      <p className="px-2 text-xs text-slate-400">{loading ? 'Loading accounts...' : 'No accounts yet.'}</p>
    )}

    <ul className="space-y-1.5">
      {accounts.map((account) => {
        const active = account.id === activeAccountId;
        const info = spending?.[account.id];
        const usage = info?.current;
        const width = Math.min(100, usage?.percentageUsed ?? 0);
        return (
          <li key={account.id}>
            <div
              role="button"
              tabIndex={0}
              onClick={() => onSelect(account.id)}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect(account.id)}
              className={`group rounded-xl px-3 py-2.5 border transition-all cursor-pointer ${
                active ? 'bg-emerald-50 border-emerald-200' : 'bg-white border-transparent hover:bg-slate-50 hover:border-slate-200'
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <p className={`text-sm font-semibold truncate ${active ? 'text-emerald-900' : 'text-slate-800'}`}>
                  {account.name}
                </p>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onEdit(account);
                  }}
                  title="Edit account"
                  className="p-0.5 rounded text-slate-300 hover:text-emerald-600 opacity-0 group-hover:opacity-100 focus:opacity-100 cursor-pointer"
                >
                  <span className="material-symbols-outlined !text-[16px]">edit</span>
                </button>
              </div>

              {usage ? (
                <>
                  <p className="text-[11px] text-slate-600 mt-0.5">
                    <strong className="text-slate-900">{formatCurrency(usage.spent)}</strong>
                    {usage.budget !== null ? ` of ${formatCurrency(usage.budget)}` : ' this month'}
                  </p>
                  {usage.budget !== null && (
                    <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden mt-1.5">
                      <div className={`h-full rounded-full ${BAR[usage.status]}`} style={{ width: `${width}%` }} />
                    </div>
                  )}
                  <p className="text-[10px] font-mono mt-1">
                    <Comparison current={usage.spent} previous={info.previousSpent} />
                  </p>
                </>
              ) : (
                <p className="text-[11px] text-slate-400 mt-0.5">{loading ? 'Loading...' : '—'}</p>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  </div>
);
