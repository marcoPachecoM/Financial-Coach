import React, { useState, useSyncExternalStore } from 'react';
import type { Transaction } from '../../types/transaction';
import type { RecurrenceFrequency } from '../../types/api';
import { FREQUENCY_LABELS } from '../../types/recurring';
import { CategoryBadge } from '../common/Badge';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { useAsync } from '../../hooks/useAsync';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { getTransactionPage } from '../../services/transactions';

const DESKTOP_QUERY = '(min-width: 768px)';
const subscribeDesktop = (onChange: () => void) => {
  const mq = window.matchMedia(DESKTOP_QUERY);
  mq.addEventListener('change', onChange);
  return () => mq.removeEventListener('change', onChange);
};
const isDesktopSnapshot = () => window.matchMedia(DESKTOP_QUERY).matches;

interface TransactionsTableProps {
  /** Heading, e.g. the day or month shown. */
  title?: string;
  accountId: string;
  /** Inclusive dates "YYYY-MM-DD" of the period shown (the selected day, or the month). */
  startDate: string;
  endDate: string;
  /** Changes after any edit elsewhere (new, edited or deleted transaction): reloads the page. */
  refreshKey: number;
  selectedCategory: string;
  /** Exact tag to filter by; null for every transaction. */
  selectedTag: string | null;
  /** Clicking a tag in a row filters by it. */
  onSelectTag: (tag: string) => void;
  onOpenUpload: () => void;
  onView: (transaction: Transaction) => void;
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
  /** Makes a transaction occurrence 0 of a new recurring transaction. */
  onMakeRecurring: (transaction: Transaction, frequency: RecurrenceFrequency) => void;
}

const FREQUENCIES = Object.keys(FREQUENCY_LABELS) as RecurrenceFrequency[];

const ITEMS_PER_PAGE = 10;

/** An amount bound typed by the user: null when empty, NaN when not a valid non-negative number. */
const parseBound = (value: string): number | null => {
  if (value.trim() === '') return null;
  const amount = Number(value);
  return Number.isFinite(amount) && amount >= 0 ? amount : Number.NaN;
};

const amountInput =
  'w-20 pl-5 pr-2 py-1.5 text-xs bg-slate-50 border rounded-xl focus:bg-white outline-hidden transition-all [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none';

const actionButton =
  'p-1.5 rounded-lg text-slate-400 transition-colors cursor-pointer';

export const TransactionsTable: React.FC<TransactionsTableProps> = ({
  title = 'Transactions',
  accountId,
  startDate,
  endDate,
  refreshKey,
  selectedCategory,
  selectedTag,
  onSelectTag,
  onOpenUpload,
  onView,
  onEdit,
  onDelete,
  onMakeRecurring,
}) => {
  const isDesktop = useSyncExternalStore(subscribeDesktop, isDesktopSnapshot, () => true);
  const [searchQuery, setSearchQuery] = useState('');
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');
  const min = parseBound(minAmount);
  const max = parseBound(maxAmount);
  const minInvalid = Number.isNaN(min);
  const maxInvalid = Number.isNaN(max);
  const rangeInverted = min !== null && max !== null && !minInvalid && !maxInvalid && min > max;
  /** While a bound is wrong, the amount filter is not applied (the inputs say why). */
  const amountFilterValid = !minInvalid && !maxInvalid && !rangeInverted;
  /** Transaction whose "Make recurring" menu is open. */
  const [menuFor, setMenuFor] = useState<string | null>(null);
  // Typing waits 300 ms before asking the server; the inputs still update at once.
  const text = useDebouncedValue(searchQuery.trim());
  const debouncedMin = useDebouncedValue(min);
  const debouncedMax = useDebouncedValue(max);
  const debouncedValid = !Number.isNaN(debouncedMin) && !Number.isNaN(debouncedMax)
    && !(debouncedMin !== null && debouncedMax !== null && debouncedMin > debouncedMax);

  // The page belongs to a filter: changing the period, category, search or amounts goes back to page 1.
  const filterKey = [accountId, startDate, endDate, selectedCategory, selectedTag, text,
    debouncedValid ? debouncedMin : null, debouncedValid ? debouncedMax : null].join('|');
  const [pageState, setPageState] = useState({ filterKey, page: 1 });
  const requestedPage = pageState.filterKey === filterKey ? pageState.page : 1;
  const setCurrentPage = (update: (page: number) => number) =>
    setPageState({ filterKey, page: update(requestedPage) });

  // Every filter and the pagination run in the backend: only the 10 rows shown are loaded.
  const result = useAsync(
    () =>
      getTransactionPage({
        accountId,
        startDate,
        endDate,
        text: text || null,
        categoryGroup: selectedCategory === 'all' ? null : selectedCategory,
        tag: selectedTag,
        // While a bound is wrong it is not sent (the inputs say why).
        minAmount: debouncedValid ? debouncedMin : null,
        maxAmount: debouncedValid ? debouncedMax : null,
        page: requestedPage,
        size: ITEMS_PER_PAGE,
      }),
    // No account yet (accounts still loading): nothing to ask for.
    accountId ? `${filterKey}|${requestedPage}|${refreshKey}` : null,
  );
  // The previous page stays on screen while the next one loads, instead of flashing "Loading".
  const page = result.data ?? result.previousData;
  const loading = result.loading;
  const totalElements = page?.totalElements ?? 0;
  const totalPages = Math.max(1, page?.totalPages ?? 1);
  const currentPage = requestedPage;
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const displayedTransactions = page?.transactions ?? [];

  // After deleting the last row of the last page, that page no longer exists: go to the new last one.
  if (result.data && result.data.transactions.length === 0 && requestedPage > 1 && result.data.totalPages < requestedPage) {
    setPageState({ filterKey, page: Math.max(1, result.data.totalPages) });
  }

  const handlePrevPage = () => {
    setCurrentPage((prev) => Math.max(prev - 1, 1));
  };

  const handleNextPage = () => {
    setCurrentPage((prev) => Math.min(prev + 1, totalPages));
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-200/80 card-shadow overflow-hidden">
      {/* Table Header Controls */}
      <div className="p-5 md:p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-bold text-slate-900 font-sans">{title}</h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
              {totalElements} records
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Newest first · expenses and income of the selected account
          </p>
        </div>

        {/* Amount range, search & actions */}
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5" role="group" aria-label="Filter by amount range">
              <span className="text-xs text-slate-500 font-medium">Amount</span>
              <div className="relative">
                <span className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400 text-xs">$</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min={0}
                  step="0.01"
                  value={minAmount}
                  onChange={(e) => setMinAmount(e.target.value)}
                  placeholder="Min"
                  aria-label="Minimum amount"
                  aria-invalid={minInvalid || rangeInverted}
                  className={`${amountInput} ${
                    minInvalid || rangeInverted
                      ? 'border-rose-400 focus:border-rose-500 focus:ring-1 focus:ring-rose-500'
                      : 'border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500'
                  }`}
                />
              </div>
              <span className="text-xs text-slate-400" aria-hidden="true">–</span>
              <div className="relative">
                <span className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400 text-xs">$</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min={0}
                  step="0.01"
                  value={maxAmount}
                  onChange={(e) => setMaxAmount(e.target.value)}
                  placeholder="Max"
                  aria-label="Maximum amount"
                  aria-invalid={maxInvalid || rangeInverted}
                  className={`${amountInput} ${
                    maxInvalid || rangeInverted
                      ? 'border-rose-400 focus:border-rose-500 focus:ring-1 focus:ring-rose-500'
                      : 'border-slate-200 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500'
                  }`}
                />
              </div>
              {(minAmount || maxAmount) && (
                <button
                  type="button"
                  onClick={() => {
                    setMinAmount('');
                    setMaxAmount('');
                  }}
                  aria-label="Clear amount range"
                  title="Clear amount range"
                  className="text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">close</span>
                </button>
              )}
            </div>
            {!amountFilterValid && (
              <p role="alert" className="text-[11px] text-rose-600 mt-1">
                {rangeInverted ? 'Min must be less than or equal to max.' : 'Amounts must be 0 or more.'}
              </p>
            )}
          </div>

          <div className="relative w-full sm:w-auto">
            <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 !text-[18px]">
              search
            </span>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search merchant, category, notes, tags..."
              className="pl-8 pr-7 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden w-full sm:w-56 transition-all"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                <span className="material-symbols-outlined !text-[14px]">close</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Transactions: Data Table on md+, Cards on mobile */}
      {isDesktop ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[640px]">
            <thead>
              <tr className="bg-slate-50/75 border-b border-slate-100 text-xs font-semibold text-slate-500 uppercase font-mono tracking-wider">
                <th className="py-3 px-5">Date</th>
                <th className="py-3 px-5">Merchant</th>
                <th className="py-3 px-5">Category</th>
                <th className="py-3 px-5 text-right">Amount</th>
                <th className="py-3 pr-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {result.error && !page ? (
                <tr>
                  <td colSpan={5} role="alert" className="py-12 text-center text-sm text-rose-600">
                    {result.error}
                  </td>
                </tr>
              ) : displayedTransactions.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-slate-400">
                    <div className="flex flex-col items-center justify-center">
                      <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">
                        receipt_long
                      </span>
                      <p className="text-sm font-medium text-slate-600">
                        {loading ? 'Loading transactions...' : 'No transactions match your criteria'}
                      </p>
                      <p className="text-xs text-slate-400 mt-1">Try clearing your filters or upload a new dataset.</p>
                      <button
                        onClick={onOpenUpload}
                        className="mt-3 text-xs text-emerald-600 hover:underline flex items-center gap-1 font-semibold cursor-pointer"
                      >
                        <span className="material-symbols-outlined !text-[16px]">add_circle</span>
                        Upload new CSV
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                displayedTransactions.map((tx) => {
                  const isExpense = tx.type === 'EXPENSE';
                  return (
                    <tr
                      key={tx.id}
                      className="hover:bg-slate-50/80 transition-colors group"
                    >
                      {/* Date */}
                      <td className="py-3.5 px-5 text-xs font-medium text-slate-500 whitespace-nowrap">
                        {formatDate(tx.datetime)}
                        <span className="block text-[11px] font-mono text-slate-400">{tx.datetime.slice(11, 16)}</span>
                      </td>

                      {/* Merchant & notes */}
                      <td className="py-3.5 px-5">
                        <div className="flex items-center gap-2.5">
                          <div className="w-8 h-8 rounded-lg bg-slate-100 border border-slate-200/60 flex items-center justify-center text-slate-700 font-semibold text-xs shrink-0 group-hover:bg-white group-hover:border-emerald-200 transition-colors">
                            {tx.name.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900 leading-tight flex items-center gap-1">
                              {tx.name}
                              {tx.recurringTransactionId && (
                                <span
                                  className="material-symbols-outlined !text-[14px] text-emerald-600"
                                  title="Recurring: the next ones are added automatically"
                                >
                                  repeat
                                </span>
                              )}
                            </p>
                            {tx.notes && (
                              <span className="text-[11px] text-slate-400" title={tx.notes}>
                                {tx.notes}
                              </span>
                            )}
                            {tx.tags.length > 0 && (
                              <span className="flex flex-wrap gap-1 mt-0.5">
                                {tx.tags.map((tag) => (
                                  <button
                                    key={tag}
                                    type="button"
                                    onClick={() => onSelectTag(tag)}
                                    title={`Show only #${tag}`}
                                    className={`px-1.5 py-px rounded-full text-[10px] font-medium border cursor-pointer transition-colors ${
                                      selectedTag?.toLowerCase() === tag.toLowerCase()
                                        ? 'bg-emerald-600 border-emerald-600 text-white'
                                        : 'bg-emerald-50 border-emerald-200 text-emerald-800 hover:bg-emerald-100'
                                    }`}
                                  >
                                    #{tag}
                                  </button>
                                ))}
                              </span>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Category */}
                      <td className="py-3.5 px-5 whitespace-nowrap">
                        <CategoryBadge group={tx.categoryGroup} />
                        {tx.category && (
                          <span className="block text-[11px] text-slate-500 mt-1 pl-1">{tx.category}</span>
                        )}
                      </td>

                      {/* Amount */}
                      <td className="py-3.5 px-5 text-right whitespace-nowrap">
                        <span
                          className={`font-semibold font-mono text-sm ${
                            isExpense ? 'text-slate-900' : 'text-emerald-600'
                          }`}
                        >
                          {isExpense ? '' : '+'}{formatCurrency(tx.amount)}
                        </span>
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 pr-4 text-right relative whitespace-nowrap">
                        <div className="inline-flex items-center gap-0.5">
                          <button
                            onClick={() => onView(tx)}
                            title="View"
                            className={`${actionButton} hover:text-slate-700 hover:bg-slate-100`}
                          >
                            <span className="material-symbols-outlined !text-[18px]">visibility</span>
                          </button>
                          <button
                            onClick={() => onEdit(tx)}
                            title="Edit"
                            className={`${actionButton} hover:text-emerald-700 hover:bg-emerald-50`}
                          >
                            <span className="material-symbols-outlined !text-[18px]">edit</span>
                          </button>
                          <button
                            onClick={() => onDelete(tx)}
                            title="Delete"
                            className={`${actionButton} hover:text-rose-600 hover:bg-rose-50`}
                          >
                            <span className="material-symbols-outlined !text-[18px]">delete</span>
                          </button>
                          {tx.recurringTransactionId ? (
                            <span className="p-1.5 text-emerald-600" title="Already recurring">
                              <span className="material-symbols-outlined !text-[18px]">event_repeat</span>
                            </span>
                          ) : (
                            <button
                              onClick={() => setMenuFor(menuFor === tx.id ? null : tx.id)}
                              title="Make recurring"
                              className={`${actionButton} hover:text-emerald-700 hover:bg-emerald-50`}
                            >
                              <span className="material-symbols-outlined !text-[18px]">event_repeat</span>
                            </button>
                          )}
                        </div>
                        {menuFor === tx.id && (
                          <div
                            className="absolute right-4 top-full -mt-2 w-44 bg-white border border-slate-200 rounded-xl shadow-xl z-30 p-1.5 text-left"
                            onMouseLeave={() => setMenuFor(null)}
                          >
                            <p className="px-2 py-1 text-[10px] font-mono font-semibold uppercase text-slate-400">
                              Make recurring
                            </p>
                            {FREQUENCIES.map((frequency) => (
                              <button
                                key={frequency}
                                onClick={() => {
                                  setMenuFor(null);
                                  onMakeRecurring(tx, frequency);
                                }}
                                className="w-full text-left px-2.5 py-1.5 rounded-lg text-xs text-slate-700 hover:bg-emerald-50 hover:text-emerald-800 cursor-pointer"
                              >
                                {FREQUENCY_LABELS[frequency]}
                              </button>
                            ))}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="p-3 space-y-3 bg-slate-50/40">
          {result.error && !page ? (
            <p role="alert" className="py-10 text-center text-sm text-rose-600">
              {result.error}
            </p>
          ) : displayedTransactions.length === 0 ? (
            <div className="py-10 px-4 text-center text-slate-400 flex flex-col items-center justify-center">
              <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">
                receipt_long
              </span>
              <p className="text-sm font-medium text-slate-600">
                {loading ? 'Loading transactions...' : 'No transactions match your criteria'}
              </p>
              <p className="text-xs text-slate-400 mt-1">Try clearing your filters or upload a new dataset.</p>
              <button
                onClick={onOpenUpload}
                className="mt-3 text-xs text-emerald-600 hover:underline flex items-center gap-1 font-semibold cursor-pointer"
              >
                <span className="material-symbols-outlined !text-[16px]">add_circle</span>
                Upload new CSV
              </button>
            </div>
          ) : (
            displayedTransactions.map((tx) => {
              const isExpense = tx.type === 'EXPENSE';
              return (
                <article
                  key={tx.id}
                  className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-3.5 space-y-3"
                >
                  {/* Card Header: Merchant + quick Edit/Delete */}
                  <div className="flex items-start justify-between gap-2.5">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-9 h-9 rounded-xl bg-slate-100 border border-slate-200/60 flex items-center justify-center text-slate-700 font-semibold text-xs shrink-0">
                        {tx.name.charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <p className="font-semibold text-sm text-slate-900 leading-tight flex items-center gap-1 truncate">
                          <span className="truncate">{tx.name}</span>
                          {tx.recurringTransactionId && (
                            <span
                              className="material-symbols-outlined !text-[14px] text-emerald-600 shrink-0"
                              title="Recurring: the next ones are added automatically"
                            >
                              repeat
                            </span>
                          )}
                        </p>
                        <p className="text-[11px] text-slate-500 mt-0.5 font-mono">
                          {formatDate(tx.datetime)} · {tx.datetime.slice(11, 16)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        onClick={() => onEdit(tx)}
                        title="Edit"
                        aria-label={`Edit ${tx.name}`}
                        className="p-1.5 rounded-lg bg-slate-50 border border-slate-200/80 text-slate-500 hover:text-emerald-700 hover:bg-emerald-50 hover:border-emerald-200 transition-colors cursor-pointer"
                      >
                        <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">edit</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => onDelete(tx)}
                        title="Delete"
                        aria-label={`Delete ${tx.name}`}
                        className="p-1.5 rounded-lg bg-rose-50/60 border border-rose-200/70 text-rose-500 hover:text-rose-700 hover:bg-rose-100 transition-colors cursor-pointer"
                      >
                        <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">delete</span>
                      </button>
                    </div>
                  </div>

                  {/* Card Details (structured rows) */}
                  <div className="divide-y divide-slate-100 border-t border-slate-100 text-xs">
                    <div className="py-2 flex items-center justify-between gap-2">
                      <span className="text-slate-400 font-medium">Amount</span>
                      <span
                        className={`font-semibold font-mono text-sm ${
                          isExpense ? 'text-slate-900' : 'text-emerald-600'
                        }`}
                      >
                        {isExpense ? '' : '+'}{formatCurrency(tx.amount)}
                      </span>
                    </div>

                    <div className="py-2 flex items-center justify-between gap-2">
                      <span className="text-slate-400 font-medium">Category</span>
                      <div className="flex items-center gap-1.5 min-w-0">
                        <CategoryBadge group={tx.categoryGroup} />
                        {tx.category && (
                          <span className="text-[11px] text-slate-500 truncate max-w-[120px]">{tx.category}</span>
                        )}
                      </div>
                    </div>

                    {tx.notes && (
                      <div className="py-2 flex items-start justify-between gap-3">
                        <span className="text-slate-400 font-medium shrink-0">Notes</span>
                        <span className="text-slate-600 text-right break-words">{tx.notes}</span>
                      </div>
                    )}

                    {tx.tags.length > 0 && (
                      <div className="py-2 flex items-start justify-between gap-3">
                        <span className="text-slate-400 font-medium shrink-0">Tags</span>
                        <div className="flex flex-wrap justify-end gap-1">
                          {tx.tags.map((tag) => (
                            <button
                              key={tag}
                              type="button"
                              onClick={() => onSelectTag(tag)}
                              title={`Show only #${tag}`}
                              className={`px-1.5 py-px rounded-full text-[10px] font-medium border cursor-pointer transition-colors ${
                                selectedTag?.toLowerCase() === tag.toLowerCase()
                                  ? 'bg-emerald-600 border-emerald-600 text-white'
                                  : 'bg-emerald-50 border-emerald-200 text-emerald-800 hover:bg-emerald-100'
                              }`}
                            >
                              #{tag}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Card Footer Actions */}
                  <div className="flex items-center gap-2 pt-1 relative">
                    <button
                      type="button"
                      onClick={() => onView(tx)}
                      className="flex-1 py-1.5 px-3 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200/80 text-xs font-semibold text-slate-700 flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
                    >
                      <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">visibility</span>
                      View details
                    </button>

                    {tx.recurringTransactionId ? (
                      <span
                        className="py-1.5 px-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs font-semibold text-emerald-700 flex items-center gap-1"
                        title="Already recurring"
                      >
                        <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">event_repeat</span>
                        Recurring
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setMenuFor(menuFor === tx.id ? null : tx.id)}
                        className="py-1.5 px-3 rounded-xl bg-emerald-50/70 hover:bg-emerald-100/80 border border-emerald-200 text-xs font-semibold text-emerald-700 flex items-center gap-1 transition-colors cursor-pointer"
                      >
                        <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">event_repeat</span>
                        Repeat
                      </button>
                    )}

                    {menuFor === tx.id && (
                      <div
                        className="absolute right-0 bottom-full mb-1.5 w-44 bg-white border border-slate-200 rounded-xl shadow-xl z-30 p-1.5 text-left"
                        onMouseLeave={() => setMenuFor(null)}
                      >
                        <p className="px-2 py-1 text-[10px] font-mono font-semibold uppercase text-slate-400">
                          Make recurring
                        </p>
                        {FREQUENCIES.map((frequency) => (
                          <button
                            key={frequency}
                            type="button"
                            onClick={() => {
                              setMenuFor(null);
                              onMakeRecurring(tx, frequency);
                            }}
                            className="w-full text-left px-2.5 py-1.5 rounded-lg text-xs text-slate-700 hover:bg-emerald-50 hover:text-emerald-800 cursor-pointer"
                          >
                            {FREQUENCY_LABELS[frequency]}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </article>
              );
            })
          )}
        </div>
      )}

      {/* Table Footer with Pagination Controls */}
      <div className="px-5 md:px-6 py-3.5 bg-slate-50/60 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <span className="font-mono text-slate-500">
          Showing {totalElements > 0 ? startIndex + 1 : 0} -{' '}
          {Math.min(startIndex + ITEMS_PER_PAGE, totalElements)} of{' '}
          {totalElements} transactions
          {loading && page && <span className="ml-2 text-slate-400">· updating…</span>}
        </span>

        {/* Pagination Navigation */}
        <div className="flex items-center gap-2">
          <button
            onClick={handlePrevPage}
            disabled={currentPage === 1}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all ${
              currentPage === 1
                ? 'border-slate-200 text-slate-300 bg-slate-50 cursor-not-allowed'
                : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-300 shadow-2xs cursor-pointer active:scale-95'
            }`}
          >
            <span className="material-symbols-outlined !text-[16px]">chevron_left</span>
            <span>Previous</span>
          </button>

          <div className="px-3 py-1 bg-white border border-slate-200 rounded-lg text-xs font-mono font-medium text-slate-700 shadow-2xs">
            Page <span className="font-semibold text-emerald-600">{currentPage}</span> of{' '}
            <span className="font-semibold">{totalPages}</span>
          </div>

          <button
            onClick={handleNextPage}
            disabled={currentPage >= totalPages || totalElements === 0}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all ${
              currentPage >= totalPages || totalElements === 0
                ? 'border-slate-200 text-slate-300 bg-slate-50 cursor-not-allowed'
                : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-300 shadow-2xs cursor-pointer active:scale-95'
            }`}
          >
            <span>Next</span>
            <span className="material-symbols-outlined !text-[16px]">chevron_right</span>
          </button>
        </div>
      </div>
    </section>
  );
};