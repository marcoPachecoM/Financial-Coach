import React, { useMemo, useState } from 'react';
import type { RecurrenceFrequency } from '../../types/api';
import type { Transaction } from '../../types/transaction';
import { formatFullDate } from '../../utils/formatters';
import { TransactionsTable } from '../dashboard/TransactionsTable';
import { CategoryFilters } from '../dashboard/CategoryFilters';
import { CalendarFilter } from './CalendarFilter';
import { useAsync } from '../../hooks/useAsync';
import { getTags } from '../../services/tags';

/** Chips shown at most: the most used tags. The search box finds any other. */
const MAX_TAG_CHIPS = 12;

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
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [tagAccount, setTagAccount] = useState(accountId);
  // Another account has other tags: forget the selected one (adjusted during render, as React recommends).
  if (accountId !== tagAccount) {
    setTagAccount(accountId);
    setSelectedTag(null);
  }
  // The account's tags, most used first; reloaded after any edit (refreshKey).
  const tags = useAsync(() => getTags(accountId), accountId ? `${accountId}|${refreshKey}` : null);
  const tagChips = (tags.data ?? []).slice(0, MAX_TAG_CHIPS);
  const toggleTag = (tag: string) =>
    setSelectedTag((current) => (current?.toLowerCase() === tag.toLowerCase() ? null : tag));

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

      {(tagChips.length > 0 || selectedTag) && (
        <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Filter by tag">
          <span className="text-xs font-semibold text-slate-500 mr-1">Tags</span>
          {selectedTag && !tagChips.some((t) => t.name.toLowerCase() === selectedTag.toLowerCase()) && (
            <button
              type="button"
              aria-pressed="true"
              onClick={() => setSelectedTag(null)}
              className="px-2.5 py-1 rounded-full text-xs font-medium border bg-emerald-600 border-emerald-600 text-white cursor-pointer"
            >
              #{selectedTag} ✕
            </button>
          )}
          {tagChips.map((tag) => {
            const active = selectedTag?.toLowerCase() === tag.name.toLowerCase();
            return (
              <button
                key={tag.name}
                type="button"
                aria-pressed={active}
                onClick={() => toggleTag(tag.name)}
                title={`${tag.transactions} transaction${tag.transactions === 1 ? '' : 's'}`}
                className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
                  active
                    ? 'bg-emerald-600 border-emerald-600 text-white'
                    : 'bg-white border-slate-200 text-slate-700 hover:bg-emerald-50 hover:border-emerald-300'
                }`}
              >
                #{tag.name}
                <span className={`ml-1 ${active ? 'text-emerald-100' : 'text-slate-400'}`}>{tag.transactions}</span>
              </button>
            );
          })}
        </div>
      )}
      <TransactionsTable
        title={title}
        accountId={accountId}
        startDate={startDate}
        endDate={endDate}
        refreshKey={refreshKey}
        selectedCategory={selectedCategory}
        selectedTag={selectedTag}
        onSelectTag={toggleTag}
        {...tableActions}
      />
    </div>
  );
};
