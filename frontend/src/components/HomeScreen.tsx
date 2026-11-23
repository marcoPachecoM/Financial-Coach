import React, { useMemo, useState } from "react";
import type { MetricSummary } from "../types/metrics";
import type { CsvUploadResponse, RecurrenceFrequency } from "../types/api";
import type { Transaction } from "../types/transaction";
import { FREQUENCY_LABELS, type RecurringTransaction } from "../types/recurring";

import { Header } from "./common/Header";
import { UploadModal } from "./common/UploadModal";
import { TransactionFormModal } from "./transactions/TransactionFormModal";
import { RecurringCard } from "./dashboard/RecurringCard";
import { MetricCards } from "./dashboard/MetricCards";
import { BudgetCard } from "./dashboard/BudgetCard";
import { CategoryFilters } from "./dashboard/CategoryFilters";
import { SpendingTrendChart } from "./dashboard/SpendingTrendChart";
import { SpendingByCategoryChart } from "./dashboard/SpendingByCategoryChart";
import { CopilotPanel } from "./copilot/CopilotPanel";
import { Sidebar, type View } from "./sidebar/Sidebar";
import { AccountFormModal } from "./sidebar/AccountFormModal";
import { TransactionsView } from "./transactions/TransactionsView";
import { TransactionDetailModal } from "./transactions/TransactionDetailModal";
import type { Account } from "../types/account";

import {
  calculateDateRangeLimits,
  formatLocalDateToISO,
  generateDatePresets,
  isDateWithinRange,
  type DatePresetId,
} from "../utils/dateHelper";
import {
  useAccounts,
  useAccountsSpending,
  useAlerts,
  useAnalyticsSummary,
  useBudgetUsage,
  useCategoryGroupSummary,
  useCurrentUser,
  useRecurringTransactions,
  useTransactions,
} from "../hooks/useDashboardData";
import { updateMonthlyBudget } from "../services/accounts";
import { getErrorMessage } from "../services/errors";
import { deleteRecurring, makeRecurring, stopRecurring } from "../services/recurring";
import { deleteTransaction } from "../services/transactions";
import { formatCurrency, formatFullDate } from "../utils/formatters";
import { useCopilot } from "../hooks/useCopilot";
import { useAuth } from "../contexts/AuthContext";

const DAY_MS = 24 * 60 * 60 * 1000;
const SIDEBAR_KEY = "financial-coach.sidebar-collapsed";

/** Remembered per browser; any storage error just means "expanded". */
const loadCollapsed = () => {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === "true";
  } catch {
    return false;
  }
};

const firstOfThisMonth = () => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1);
};

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
  const [isNewTransactionOpen, setIsNewTransactionOpen] = useState(false);
  const [busyRecurringId, setBusyRecurringId] = useState<string | null>(null);

  // 2b. Layout and the Transactions view.
  const [view, setView] = useState<View>("dashboard");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(loadCollapsed);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [calendarMonth, setCalendarMonth] = useState(firstOfThisMonth);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [viewingTransaction, setViewingTransaction] = useState<Transaction | null>(null);
  const [accountModal, setAccountModal] = useState<{ open: boolean; account: Account | null }>({
    open: false,
    account: null,
  });

  const toggleSidebar = () => {
    if (window.matchMedia("(min-width: 1024px)").matches) {
      const next = !sidebarCollapsed;
      setSidebarCollapsed(next);
      try {
        localStorage.setItem(SIDEBAR_KEY, String(next));
      } catch {
        // Not remembered: fine.
      }
    } else {
      setMobileSidebarOpen((open) => !open);
    }
  };

  const selectAccount = (id: string) => {
    setSelectedAccountId(id);
    setSelectedPresetId("all_time");
    setSelectedCategory("all");
    setSelectedDay(null);
  };

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
  const recurring = useRecurringTransactions(accountId);
  const alerts = useAlerts(accountId);
  const categorySummary = useMemo(() => categories.data ?? [], [categories.data]);
  const recurringList = useMemo(() => recurring.data ?? [], [recurring.data]);
  const alertList = useMemo(() => alerts.data ?? [], [alerts.data]);
  const accountIds = useMemo(() => accountList.map((a) => a.id), [accountList]);
  const spending = useAccountsSpending(accountIds);

  /** After any change: every card depends on the transactions. */
  const refreshAll = () => {
    accounts.refetch();
    allTransactions.refetch();
    summary.refetch();
    categories.refetch();
    budget.refetch();
    recurring.refetch();
    alerts.refetch();
    spending.refetch();
  };

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

  const copilot = useCopilot({
    accountId,
    alerts: alertList,
    onDataChanged: refreshAll,
  });

  const handleImported = (result: CsvUploadResponse) => {
    refreshAll();
    setSelectedPresetId("all_time");
    setSelectedCategory("all");
    copilot.say(
      `Imported ${result.records_imported} transaction(s)` +
        (result.records_duplicated ? `; ${result.records_duplicated} already imported were skipped` : "") +
        (result.recurring_matched ? `; ${result.recurring_matched} matched recurring charges` : "") +
        ".",
    );
  };

  const handleSaved = (tx: Transaction, repeated: RecurrenceFrequency | null) => {
    refreshAll();
    if (editingTransaction) {
      copilot.say(`Updated ${tx.name}.`);
      return;
    }
    copilot.say(
      `Added ${tx.name} (${formatCurrency(Math.abs(tx.amount))}).` +
        (repeated ? ` It repeats ${FREQUENCY_LABELS[repeated].toLowerCase()}: the next ones are added automatically.` : ""),
    );
  };

  /** Runs a change on a recurring transaction, reporting the outcome in the copilot chat. */
  const withRecurring = async (id: string, action: () => Promise<string>) => {
    setBusyRecurringId(id);
    try {
      copilot.say(await action());
      refreshAll();
    } catch (error) {
      copilot.say(`I couldn't do that: ${getErrorMessage(error)}`);
    } finally {
      setBusyRecurringId(null);
    }
  };

  const handleMakeRecurring = (tx: Transaction, frequency: RecurrenceFrequency) =>
    withRecurring(tx.id, async () => {
      const rule = await makeRecurring(tx.id, frequency);
      return (
        `${tx.name} is now recurring (${FREQUENCY_LABELS[frequency].toLowerCase()}).` +
        (rule.nextDueDate ? ` Next one: ${formatFullDate(rule.nextDueDate)}.` : "")
      );
    });

  const handleStopRecurring = (rule: RecurringTransaction) =>
    withRecurring(rule.id, async () => {
      await stopRecurring(rule.id, formatLocalDateToISO(new Date()));
      return `${rule.name} will no longer be added automatically.`;
    });

  const handleDeleteRecurring = (rule: RecurringTransaction) => {
    if (!window.confirm(`Delete the recurring transaction "${rule.name}"? Transactions already added are kept.`)) {
      return;
    }
    return withRecurring(rule.id, async () => {
      await deleteRecurring(rule.id);
      return `Deleted the recurring transaction ${rule.name}. Its past transactions were kept.`;
    });
  };

  const handleDeleteTransaction = async (tx: Transaction) => {
    const warning = tx.recurringTransactionId
      ? " It is an occurrence of a recurring transaction: it will not be added again."
      : "";
    if (!window.confirm(`Delete "${tx.name}" (${formatCurrency(Math.abs(tx.amount))})?${warning}`)) return;
    try {
      await deleteTransaction(tx.id);
      copilot.say(`Deleted ${tx.name}.`);
      refreshAll();
    } catch (error) {
      copilot.say(`I couldn't delete it: ${getErrorMessage(error)}`);
    }
  };

  const handleAccountSaved = (account: Account, created: boolean) => {
    refreshAll();
    if (created) selectAccount(account.id);
    copilot.say(created ? `Created the account ${account.name}.` : `Saved the account ${account.name}.`);
  };

  const handleSaveBudget = async (monthlyBudget: number | null) => {
    if (!accountId) return;
    try {
      await updateMonthlyBudget(accountId, monthlyBudget);
    } catch (error) {
      throw new Error(getErrorMessage(error), { cause: error });
    }
    refreshAll();
  };

  const loadError =
    accounts.error ?? allTransactions.error ?? summary.error ?? categories.error ?? recurring.error ?? alerts.error;
  const noAccounts = !accounts.loading && accountList.length === 0 && !accounts.error;

  return (
    <div className="bg-[#f7f9fb] text-slate-900 min-h-screen flex flex-col font-sans">
      <Header
        activeAccountName={activeAccount?.name ?? null}
        onToggleSidebar={toggleSidebar}
        onOpenUpload={() => setIsUploadModalOpen(true)}
        onOpenNewTransaction={() => setIsNewTransactionOpen(true)}
        onLogout={logout}
      />

      <div className="flex flex-1 w-full">
      <Sidebar
        view={view}
        onChangeView={setView}
        collapsed={sidebarCollapsed}
        mobileOpen={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
        accounts={accountList}
        activeAccountId={accountId}
        spending={spending.data}
        loadingAccounts={accounts.loading || spending.loading}
        onSelectAccount={selectAccount}
        onEditAccount={(account) => setAccountModal({ open: true, account })}
        onCreateAccount={() => setAccountModal({ open: true, account: null })}
      />

      <main className="flex-1 min-w-0 w-full max-w-[1440px] mx-auto p-4 md:p-8">
        <div className="flex flex-col lg:flex-row gap-6 w-full max-w-full">
          <div className="flex-1 min-w-0 flex flex-col gap-6">
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
            ) : view === "transactions" ? (
              <TransactionsView
                transactions={transactions}
                loading={allTransactions.loading}
                month={calendarMonth}
                selectedDay={selectedDay}
                selectedCategory={selectedCategory}
                onSelectCategory={setSelectedCategory}
                onChangeMonth={setCalendarMonth}
                onSelectDay={setSelectedDay}
                onOpenUpload={() => setIsUploadModalOpen(true)}
                onView={setViewingTransaction}
                onEdit={setEditingTransaction}
                onDelete={handleDeleteTransaction}
                onMakeRecurring={handleMakeRecurring}
              />
            ) : (
              <>
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

                <MetricCards metrics={metrics} />

                <BudgetCard usage={budget.data} loading={budget.loading} onSaveBudget={handleSaveBudget} />

                <RecurringCard
                  rules={recurringList}
                  loading={recurring.loading}
                  busyId={busyRecurringId}
                  onStop={handleStopRecurring}
                  onDelete={handleDeleteRecurring}
                />

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

              </>
            )}
          </div>

          <CopilotPanel
            insights={copilot.insights}
            busyInsightId={copilot.busyInsightId}
            messages={copilot.messages}
            thinking={copilot.thinking}
            busy={copilot.busy}
            onInsightAction={copilot.runAction}
            onDismissInsight={copilot.dismissInsight}
            onSendMessage={copilot.sendMessage}
            onStop={copilot.stop}
          />
        </div>
      </main>
      </div>

      <UploadModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        onImported={handleImported}
      />

      <TransactionFormModal
        key={editingTransaction?.id ?? "new"}
        isOpen={isNewTransactionOpen || editingTransaction !== null}
        accountId={accountId}
        accountName={activeAccount?.name ?? ""}
        transaction={editingTransaction}
        onClose={() => {
          setIsNewTransactionOpen(false);
          setEditingTransaction(null);
        }}
        onSaved={handleSaved}
      />

      <TransactionDetailModal
        transaction={viewingTransaction}
        accountName={activeAccount?.name ?? ""}
        onClose={() => setViewingTransaction(null)}
        onEdit={(tx) => {
          setViewingTransaction(null);
          setEditingTransaction(tx);
        }}
      />

      <AccountFormModal
        key={accountModal.open ? (accountModal.account?.id ?? "new-account") : "closed"}
        isOpen={accountModal.open}
        account={accountModal.account}
        onClose={() => setAccountModal({ open: false, account: null })}
        onSaved={handleAccountSaved}
      />
    </div>
  );
};
