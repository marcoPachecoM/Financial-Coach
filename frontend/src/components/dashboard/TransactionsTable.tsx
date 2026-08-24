import React, { useState, useMemo, useEffect } from 'react';
import type { Transaction } from '../../types/transaction';
import { CategoryBadge } from '../common/Badge';
import { formatCurrency, formatDate } from '../../utils/formatters';

interface TransactionsTableProps {
  transactions: Transaction[];
  selectedCategory: string;
  onOpenUpload: () => void;
}

const ITEMS_PER_PAGE = 6;

export const TransactionsTable: React.FC<TransactionsTableProps> = ({
  transactions,
  selectedCategory,
  onOpenUpload,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);

  // Reset pagination to page 1 whenever category or search query changes
  useEffect(() => {
    setCurrentPage(1);
  }, [selectedCategory, searchQuery]);

  // Filter transactions based on selected category & search query
  const filteredTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      const categoryName = tx.category;

      // 1. Category Filter Dropdown
      if (selectedCategory !== 'all') {
        if (categoryName.toLowerCase() !== selectedCategory.toLowerCase()) {
          return false;
        }
      }

      // 2. Search Query Filter (Safe String Casting for numeric IDs & MCCs)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const provider = (tx.provider_name || '').toLowerCase();
        const mccStr = String(tx.mcc);
        const idStr = String(tx.id || '').toLowerCase();
        const amountStr = String(tx.amount || '');
        const catStr = categoryName.toLowerCase();

        return (
          provider.includes(q) ||
          mccStr.includes(q) ||
          idStr.includes(q) ||
          amountStr.includes(q) ||
          catStr.includes(q)
        );
      }

      return true;
    });
  }, [transactions, selectedCategory, searchQuery]);

  const totalPages = Math.max(1, Math.ceil(filteredTransactions.length / ITEMS_PER_PAGE));
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const displayedTransactions = filteredTransactions.slice(
    startIndex,
    startIndex + ITEMS_PER_PAGE,
  );

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
            <h3 className="text-lg font-bold text-slate-900 font-sans">Recent Transactions</h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
              {filteredTransactions.length} records
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Schema: <span className="font-mono text-slate-600">id, date, provider_name, amount, mcc</span>
          </p>
        </div>

        {/* Search & Actions */}
        <div className="flex items-center gap-2.5">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 !text-[18px]">
              search
            </span>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search merchant or MCC..."
              className="pl-8 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-hidden w-48 sm:w-56 transition-all"
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

      {/* Transactions Data Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse min-w-[640px]">
          <thead>
            <tr className="bg-slate-50/75 border-b border-slate-100 text-xs font-semibold text-slate-500 uppercase font-mono tracking-wider">
              <th className="py-3 px-5">Date</th>
              <th className="py-3 px-5">Provider / Merchant</th>
              <th className="py-3 px-5">Category & MCC</th>
              <th className="py-3 px-5 text-right">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {displayedTransactions.length === 0 ? (
              <tr>
                <td colSpan={4} className="py-12 text-center text-slate-400">
                  <div className="flex flex-col items-center justify-center">
                    <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">
                      receipt_long
                    </span>
                    <p className="text-sm font-medium text-slate-600">No transactions match your criteria</p>
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
                const isExpense = tx.amount < 0;
                return (
                  <tr
                    key={tx.id}
                    className="hover:bg-slate-50/80 transition-colors group"
                  >
                    {/* Date */}
                    <td className="py-3.5 px-5 text-xs font-medium text-slate-500 whitespace-nowrap">
                      {formatDate(tx.date)}
                    </td>

                    {/* Provider Name & ID */}
                    <td className="py-3.5 px-5">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg bg-slate-100 border border-slate-200/60 flex items-center justify-center text-slate-700 font-semibold text-xs shrink-0 group-hover:bg-white group-hover:border-emerald-200 transition-colors">
                          {(tx.provider_name || 'U').charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <p className="font-semibold text-slate-900 leading-tight">
                            {tx.provider_name || 'Unknown'}
                          </p>
                          <span className="text-[11px] font-mono text-slate-400">
                            ID: {tx.id}
                          </span>
                        </div>
                      </div>
                    </td>

                    {/* Category & MCC */}
                    <td className="py-3.5 px-5 whitespace-nowrap">
                      <CategoryBadge mcc={tx.mcc ?? 0} category={tx.category} />
                    </td>

                    {/* Amount */}
                    <td className="py-3.5 px-5 text-right whitespace-nowrap">
                      <span
                        className={`font-semibold font-mono text-sm ${
                          isExpense ? 'text-slate-900' : 'text-emerald-600'
                        }`}
                      >
                        {formatCurrency(tx.amount)}
                      </span>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Table Footer with Pagination Controls */}
      <div className="px-5 md:px-6 py-3.5 bg-slate-50/60 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <span className="font-mono text-slate-500">
          Showing {filteredTransactions.length > 0 ? startIndex + 1 : 0} -{' '}
          {Math.min(startIndex + ITEMS_PER_PAGE, filteredTransactions.length)} of{' '}
          {filteredTransactions.length} transactions
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
            disabled={currentPage === totalPages || filteredTransactions.length === 0}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all ${
              currentPage === totalPages || filteredTransactions.length === 0
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