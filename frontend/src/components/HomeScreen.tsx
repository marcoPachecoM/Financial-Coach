import React, { useMemo, useState } from "react";
import type { MetricSummary } from "../types/metrics";
import type { CsvUploadResponse } from "../types/api";

import { Header } from "./common/Header";
import { UploadModal } from "./common/UploadModal";
import { MetricCards } from "./dashboard/MetricCards";
import { BudgetCard } from "./dashboard/BudgetCard";
import { CategoryFilters } from "./dashboard/CategoryFilters";
import { SpendingTrendChart } from "./dashboard/SpendingTrendChart";
import { SpendingByCategoryChart } from "./dashboard/SpendingByCategoryChart";
import { TransactionsTable } from "./dashboard/TransactionsTable";
import { CopilotPanel } from "./copilot/CopilotPanel";

import {
  calculateDateRangeLimits,
  formatLocalDateToISO,
  generateDatePresets,
  isDateWithinRange,
  type DatePresetId,
} from "../utils/dateHelper";
import {
  useAccounts,
  useAnalyticsSummary,
  useBudgetUsage,
  useCategoryGroupSummary,
  useCurrentUser,
  useTransactions,
} from "../hooks/useDashboardData";
import { useLocalCopilot } from "../hooks/useLocalCopilot";
import { useAuth } from "../contexts/AuthContext";

const DAY_MS = 24 * 60 * 60 * 1000;

const greeting = () => {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 19 ? "Good afternoon" : "Good evening";
};

export const HomeScreen: React.FC = () => {
  const { logout } = useAuth();
  const me = useCurrentUser();

  // 1. Accounts: the selected one, or the first one when nothing (valid) is selected.
  const accounts = useAccounts();
  const accountList = useMemo(() => accounts.data ?? [], [accounts.data]);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const activeAccount = accountList.find((a) => a.id === selectedAccountId) ?? accountList[0] ?? null;
  const accountId = activeAccount?.id ?? null;

  // 2. Filters
  const [selectedPresetId, setSelectedPresetId] = useState<DatePresetId>("all_time");
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("all");
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);

  // 3. All transactions of the account: the presets are anchored to its latest transaction,
  //    and the table and trend chart filter them locally.
  const allTransactions = useTransactions(accountId ? { accountId } : null);
  const transactions = useMemo(() => allTransactions.data ?? [], [allTransactions.data]);

  const datePresets = useMemo(() => generateDatePresets(calculateDateRangeLimits(transactions)), [transactions]);
  const activePreset = datePresets.find((p) => p.id === selectedPresetId) ?? datePresets[0];
  const isAllTime = selectedPresetId === "all_time";

  const dateFilteredTransactions = useMemo(
    () =>
      isAllTime
        ? transactions
        : transactions.filter((tx) => isDateWithinRange(tx.datetime, activePreset.startDate, activePreset.endDate)),
    [transactions, activePreset, isAllTime],
  );

  // 4. Aggregates computed by the backend for the same period.
  const periodFilters = useMemo(
    () =>
      accountId
        ? {
            accountId,
            startDate: isAllTime ? undefined : formatLocalDateToISO(activePreset.startDate),
            endDate: isAllTime ? undefined : formatLocalDateToISO(activePreset.endDate),
          }
        : null,
    [accountId, activePreset, isAllTime],
  );
  const summary = useAnalyticsSummary(periodFilters);
  const categories = useCategoryGroupSummary(periodFilters);
  const budget = useBudgetUsage(accountId);
  const categorySummary = useMemo(() => categories.data ?? [], [categories.data]);

  const metrics: MetricSummary = useMemo(() => {
    const days = Math.max(
      1,
      Math.round((activePreset.endDate.getTime() - activePreset.startDate.getTime()) / DAY_MS) + 1,
    );
    const totalExpenses = summary.data?.totalExpenses ?? 0;
    const expenseCount = summary.data?.expenseCount ?? 0;
    const top = categorySummary[0];
    return {
      totalIncome: summary.data?.totalIncome ?? 0,
      totalExpenses,
      net: summary.data?.net ?? 0,
      avgDailySpend: totalExpenses / days,
      transactionsPerDay: expenseCount / days,
      totalTransactionsCount: expenseCount,
      daysInRange: days,
      topCategoryName: top?.categoryGroup ?? "N/A",
      topCategoryAmount: top?.amount ?? 0,
      topCategoryPercentage: top?.percentage ?? 0,
    };
  }, [summary.data, categorySummary, activePreset]);

  const copilot = useLocalCopilot(dateFilteredTransactions, budget.data, activePreset.sublabel);

  const handleImported = (result: CsvUploadResponse) => {
    accounts.refetch();
    allTransactions.refetch();
    summary.refetch();
    categories.refetch();
    budget.refetch();
    setSelectedPresetId("all_time");
    setSelectedCategory("all");
    copilot.say(
      `Imported ${result.records_imported} transaction(s)` +
        (result.records_duplicated ? `; ${result.records_duplicated} already imported were skipped.` : "."),
    );
  };

  const loadError = accounts.error ?? allTransactions.error ?? summary.error ?? categories.error;
  const noAccounts = !accounts.loading && accountList.length === 0 && !accounts.error;

  return (
    <div className="bg-[#f7f9fb] text-slate-900 min-h-screen flex flex-col font-sans">
      <Header
        accounts={accountList}
        activeAccountId={accountId}
        onSelectAccount={(id) => {
          setSelectedAccountId(id);
          setSelectedPresetId("all_time");
          setSelectedCategory("all");
        }}
        loadingAccounts={accounts.loading}
        onOpenUpload={() => setIsUploadModalOpen(true)}
        onLogout={logout}
      />

      <main className="flex-1 w-full max-w-[1440px] mx-auto p-4 md:p-8">
        <div className="flex flex-col lg:flex-row gap-6 w-full max-w-full">
          <div className="flex-1 min-w-0 flex flex-col gap-6">
            {/* Greeting & Date Selector */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                  {greeting()}, {me.data?.username ?? "there"}! <span className="animate-bounce">👋</span>
                </h2>
                <p className="text-sm text-slate-500 mt-0.5">
                  Here's your financial overview for{" "}
                  <strong className="text-slate-700 font-semibold">
                    {activeAccount ? activeAccount.name : "your accounts"}
                  </strong>{" "}
                  <span className="text-slate-400 font-mono text-xs">
                    ({activePreset.sublabel})
                  </span>
                </p>
              </div>

              <div className="flex items-center gap-2 relative">
                <button
                  onClick={() => setIsDatePickerOpen(!isDatePickerOpen)}
                  className="flex items-center gap-2 px-3.5 py-2 bg-white border border-slate-200 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-300 transition-all shadow-xs cursor-pointer"
                >
                  <span className="material-symbols-outlined !text-[16px] text-slate-500">
                    calendar_today
                  </span>
                  <span>{activePreset.label}</span>
                  <span className="text-[11px] text-slate-400 font-mono hidden sm:inline">
                    ({activePreset.sublabel})
                  </span>
                  <span className="material-symbols-outlined !text-[16px] text-slate-400">
                    expand_more
                  </span>
                </button>

                {isDatePickerOpen && (
                  <div
                    className="absolute right-0 top-full mt-1.5 w-72 bg-white border border-slate-200 rounded-xl shadow-xl z-30 p-2 animate-in fade-in"
                    onMouseLeave={() => setIsDatePickerOpen(false)}
                  >
                    <div className="px-2 py-1 text-[10px] font-mono font-semibold uppercase text-slate-400 border-b border-slate-100 mb-1">
                      Filter By Date Range
                    </div>
                    {datePresets.map((preset) => (
                      <button
                        key={preset.id}
                        onClick={() => {
                          setSelectedPresetId(preset.id);
                          setIsDatePickerOpen(false);
                        }}
                        className={`w-full text-left px-3 py-2 rounded-lg text-xs transition-colors flex items-center justify-between ${selectedPresetId === preset.id
                          ? "bg-emerald-50 text-emerald-900 font-semibold"
                          : "text-slate-700 hover:bg-slate-50"
                          }`}
                      >
                        <div>
                          <p className="font-medium text-slate-900">
                            {preset.label}
                          </p>
                          <p className="text-[11px] text-slate-500 font-mono">
                            {preset.sublabel}
                          </p>
                        </div>
                        {selectedPresetId === preset.id && (
                          <span className="material-symbols-outlined !text-[16px] text-emerald-600">
                            check
                          </span>
                        )}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>


            {loadError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm flex items-center gap-2">
                <span className="material-symbols-outlined !text-[18px]">error</span>
                {loadError}
              </div>
            )}

            {noAccounts ? (
              <div className="bg-white rounded-2xl p-10 border border-slate-200/80 card-shadow text-center">
                <span className="material-symbols-outlined !text-[40px] text-slate-300">account_balance</span>
                <p className="mt-2 font-semibold text-slate-900">No accounts yet</p>
                <p className="text-sm text-slate-500 mt-1">
                  Import a CSV: its <span className="font-mono">Account</span> column creates the accounts.
                </p>
                <button
                  onClick={() => setIsUploadModalOpen(true)}
                  className="mt-4 bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-xl text-sm font-semibold cursor-pointer"
                >
                  Import CSV
                </button>
              </div>
            ) : (
              <>
                <MetricCards metrics={metrics} />

                <BudgetCard usage={budget.data} loading={budget.loading} />

                <div className="flex items-center justify-between gap-2 pt-1">
                  <CategoryFilters
                    categories={categorySummary.map((c) => c.categoryGroup)}
                    selectedCategory={selectedCategory}
                    onSelectCategory={setSelectedCategory}
                  />
                </div>

                <SpendingTrendChart transactions={dateFilteredTransactions} selectedCategory={selectedCategory} />

                <SpendingByCategoryChart
                  categorySummary={categorySummary}
                  loading={categories.loading}
                  selectedCategory={selectedCategory}
                  onSelectCategory={setSelectedCategory}
                />

                <TransactionsTable
                  transactions={dateFilteredTransactions}
                  selectedCategory={selectedCategory}
                  onOpenUpload={() => setIsUploadModalOpen(true)}
                />
              </>
            )}
          </div>

          <CopilotPanel
            insights={copilot.insights}
            messages={copilot.messages}
            onExecuteInsight={copilot.executeInsight}
            onDismissInsight={copilot.dismissInsight}
            onSendMessage={copilot.sendMessage}
          />
        </div>
      </main>

      <UploadModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        onImported={handleImported}
      />
    </div>
  );
};
