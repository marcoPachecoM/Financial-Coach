import React from 'react';
import type { Transaction } from '../../types/transaction';
import { CategoryBadge } from '../common/Badge';
import { formatCurrency, formatFullDate } from '../../utils/formatters';

interface TransactionDetailModalProps {
  transaction: Transaction | null;
  accountName: string;
  onClose: () => void;
  onEdit: (transaction: Transaction) => void;
}

/** Read-only view of every field of a transaction. */
export const TransactionDetailModal: React.FC<TransactionDetailModalProps> = ({
  transaction,
  accountName,
  onClose,
  onEdit,
}) => {
  if (!transaction) return null;
  const isExpense = transaction.type === 'EXPENSE';

  const row = (label: string, value: React.ReactNode) => (
    <div className="flex items-start justify-between gap-4 py-2.5">
      <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">{label}</dt>
      <dd className="text-sm text-slate-800 text-right">{value}</dd>
    </div>
  );

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6 border-b border-slate-100 bg-slate-50/50 flex items-start justify-between gap-3">
          <div>
            <p className="text-xs font-mono uppercase tracking-wider text-slate-400">{isExpense ? 'Expense' : 'Income'}</p>
            <h2 className="text-lg font-bold text-slate-900 flex items-center gap-1.5">
              {transaction.name}
              {transaction.recurringTransactionId && (
                <span className="material-symbols-outlined !text-[18px] text-emerald-600" title="Recurring">
                  repeat
                </span>
              )}
            </h2>
            <p className={`text-2xl font-bold font-mono mt-1 ${isExpense ? 'text-slate-900' : 'text-emerald-600'}`}>
              {isExpense ? '' : '+'}
              {formatCurrency(Math.abs(transaction.amount))}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 rounded-lg p-1.5 hover:bg-slate-100 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[20px]">close</span>
          </button>
        </div>

        <dl className="px-6 py-2 divide-y divide-slate-100">
          {row('Date', `${formatFullDate(transaction.datetime)} · ${transaction.datetime.slice(11, 16)}`)}
          {row('Account', accountName)}
          {row('Group', <CategoryBadge group={transaction.categoryGroup} />)}
          {row('Category', transaction.category ?? <span className="text-slate-400">—</span>)}
          {row('Notes', transaction.notes ?? <span className="text-slate-400">—</span>)}
          {row(
            'Tags',
            transaction.tags.length > 0 ? (
              <span className="flex flex-wrap gap-1">
                {transaction.tags.map((tag) => (
                  <span
                    key={tag}
                    className="px-2 py-0.5 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-medium"
                  >
                    #{tag}
                  </span>
                ))}
              </span>
            ) : (
              <span className="text-slate-400">—</span>
            ),
          )}
          {row(
            'Recurring',
            transaction.recurringTransactionId ? 'Yes, the next ones are added automatically' : 'No',
          )}
        </dl>

        <div className="px-6 py-4 border-t border-slate-100 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
          >
            Close
          </button>
          <button
            onClick={() => onEdit(transaction)}
            className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-xl text-xs font-semibold flex items-center gap-1 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[16px]">edit</span>
            Edit
          </button>
        </div>
      </div>
    </div>
  );
};
