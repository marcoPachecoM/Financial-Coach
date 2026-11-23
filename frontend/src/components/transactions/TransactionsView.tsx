import React, { useMemo } from 'react';
import type { RecurrenceFrequency } from '../../types/api';
import type { Transaction } from '../../types/transaction';
import { formatFullDate } from '../../utils/formatters';
import { TransactionsTable } from '../dashboard/TransactionsTable';
import { CategoryFilters } from '../dashboard/CategoryFilters';
import { CalendarFilter } from './CalendarFilter';

interface TransactionsViewProps {
  accountId: string;
  /** Changes after any edit: the table reloads its page. */
  refreshKey: number;
  /** Every transaction of the account: for the calendar and the category chips (the table loads its own page). */
  transactions: Transaction[];
  month: Date;
  selectedDay: string | null;
  selectedCategory: string;
  onSelectCategory: (category: string) => void;
  onChangeMonth: (month: Date) => void;
  onSelectDay: (day: string | null) => void;
  onOpenUpload: () => void;
  onView: (transaction: Transaction) => void;
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
  onMakeRecurring: (transaction: Transaction, frequency: RecurrenceFrequency) => void;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Calendar on top; below, the transactions of the selected day (or of the month shown). */
export const TransactionsView: React.FC<TransactionsViewProps> = ({
  accountId,
  refreshKey,
  transactions,
  month,
  selectedDay,
  selectedCategory,
  onSelectCategory,
  onChangeMonth,
  onSelectDay,
  ...tableActions
}) => {
  const monthPrefix = `${month.getFullYear()}-${pad(month.getMonth() + 1)}`;
  const lastDay = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  // The period the table asks the server for: the selected day, or the whole month shown.
  const startDate = selectedDay ?? `${monthPrefix}-01`;
  const endDate = selectedDay ?? `${monthPrefix}-${pad(lastDay)}`;
  const visible = useMemo(
    () =>
      transactions.filter((tx) =>
        selectedDay ? tx.datetime.startsWith(selectedDay) : tx.datetime.startsWith(monthPrefix),
      ),
    [transactions, selectedDay, monthPrefix],
  );

  // Groups present in the period, most used first.
  const groups = useMemo(() => {
    const counts = new Map<string, number>();
    visible.forEach((tx) => counts.set(tx.categoryGroup, (counts.get(tx.categoryGroup) ?? 0) + 1));
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([group]) => group);
  }, [visible]);

  const title = selectedDay
    ? formatFullDate(selectedDay)
    : month.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900 tracking-tight">Transactions</h2>
        <p className="text-sm text-slate-500 mt-0.5">Pick a day to see only its transactions.</p>
      </div>

      <CalendarFilter
        month={month}
        selectedDay={selectedDay}
        transactions={transactions}
        onChangeMonth={onChangeMonth}
        onSelectDay={onSelectDay}
      />

      {groups.length > 0 && (
        <CategoryFilters categories={groups} selectedCategory={selectedCategory} onSelectCategory={onSelectCategory} />
      )}

      <TransactionsTable
        title={title}
        accountId={accountId}
        startDate={startDate}
        endDate={endDate}
        refreshKey={refreshKey}
        selectedCategory={selectedCategory}
        {...tableActions}
      />
    </div>
  );
};
