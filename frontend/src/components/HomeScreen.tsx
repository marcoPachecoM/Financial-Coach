import React, { useState, useMemo, useEffect } from "react";
import type { Transaction } from "../types/transaction";
import type { MetricSummary } from "../types/metrics";
import type { Insight, ChatMessage } from "../types/copilot";

import { Header } from "./common/Header";
import { UploadModal } from "./common/UploadModal";
import { MetricCards } from "./dashboard/MetricCards";
import { CategoryFilters } from "./dashboard/CategoryFilters";
import { SpendingTrendChart } from "./dashboard/SpendingTrendChart";
import { SpendingByCategoryChart } from "./dashboard/SpendingByCategoryChart";
import { useSpendingCategoryData } from "../hooks/useSpendingCategoryData";
import { TransactionsTable } from "./dashboard/TransactionsTable";
import { CopilotPanel } from "./copilot/CopilotPanel";

import { INITIAL_METRIC_SUMMARY } from "../data/mockMetrics";
import { INITIAL_INSIGHTS, INITIAL_CHAT_MESSAGES } from "../data/mockInsights";

import { calculateDateRangeLimits, formatLocalDateToISO, generateDatePresets, isDateWithinRange, type DatePresetId, type DateRangeLimit } from "../utils/dateHelper";
import { useTransactions } from "../hooks/useTransactions";
import { useConnections } from "../hooks/useConnections";
import { useAuth } from "../contexts/AuthContext";

interface HomeScreenProps {
  initialTransactions?: Transaction[];
}

export const HomeScreen: React.FC<HomeScreenProps> = () => {
  const { logout } = useAuth();

  // 1. Database Connections Hook
  const {
    connections,
    loading: isConnectionsLoading,
    refetch: refetchConnections,
  } = useConnections();

  const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);

  // Auto-select first connection once loaded if none is active
  useEffect(() => {
    if (connections.length > 0 && selectedConnectionId === null) {
      setSelectedConnectionId(connections[0].id);
    }
  }, [connections, selectedConnectionId]);

  const activeConnectionIdStr = useMemo(() => {
    if (selectedConnectionId !== null) return String(selectedConnectionId);
    if (connections.length > 0) return String(connections[0].id);
    return "1";
  }, [selectedConnectionId, connections]);

  // 2. Preset & Filter States
  const [selectedPresetId, setSelectedPresetId] =
    useState<DatePresetId>("all_time");
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("all");

  // 3. CSV Upload & Copilot States
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [insights, setInsights] = useState<Insight[]>(INITIAL_INSIGHTS);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>(
    INITIAL_CHAT_MESSAGES,
  );

  // 4. Date Limits initialization
  const [localDateLimits, setLocalDateLimits] = useState<DateRangeLimit>(() => {
    const now = new Date();
    const pastYear = new Date(new Date().setFullYear(now.getFullYear() - 1));
    return {
      minDate: pastYear,
      maxDate: now,
      minDateStr: pastYear.toISOString().split("T")[0],
      maxDateStr: now.toISOString().split("T")[0],
    };
  });

  const datePresets = useMemo(
    () => generateDatePresets(localDateLimits),
    [localDateLimits],
  );

  // Active Date Preset
  const activePreset = useMemo(() => {
    return datePresets.find((p) => p.id === selectedPresetId) || datePresets[0];
  }, [datePresets, selectedPresetId]);

  // Pass undefined when 'all_time' is selected so API fetches without date parameters
  const startDateStr = useMemo(() => {
    if (selectedPresetId === "all_time") return undefined;
    return activePreset?.startDate
      ? formatLocalDateToISO(activePreset.startDate)
      : undefined;
  }, [activePreset, selectedPresetId]);

  const endDateStr = useMemo(() => {
    if (selectedPresetId === "all_time") return undefined;
    return activePreset?.endDate
      ? formatLocalDateToISO(activePreset.endDate)
      : undefined;
  }, [activePreset, selectedPresetId]);

  // 5. Transactions Hook (Driven dynamically by active selectedConnectionId)
  const {
    transactions,
    setTransactions,
    refetch: refetchTransactions,
  } = useTransactions({
    connectionId: activeConnectionIdStr,
    startDate: startDateStr,
    endDate: endDateStr,
  });

  // Update date boundaries dynamically when initial transaction payload loads
  useEffect(() => {
    if (transactions.length > 0) {
      setLocalDateLimits(calculateDateRangeLimits(transactions));
    }
  }, [transactions]);

  // Fetch Category Summary from backend (Only includes dates if preset !== 'all_time')
  const {
    data: categorySummary,
    loading: isCategoryLoading,
  } = useSpendingCategoryData(activeConnectionIdStr, startDateStr, endDateStr);

  // Client-side fallback filter
  const dateFilteredTransactions = useMemo(() => {
    if (!activePreset || selectedPresetId === "all_time") return transactions;
    return transactions.filter((tx) =>
      isDateWithinRange(tx.date, activePreset.startDate, activePreset.endDate),
    );
  }, [transactions, activePreset, selectedPresetId]);

  // Metrics calculation (dynamically reactive to dateFilteredTransactions & active preset)
  const metrics: MetricSummary = useMemo(() => {
    const totalSpent = dateFilteredTransactions.reduce(
      (sum, t) => sum + Math.abs(t.amount),
      0,
    );

    const baseBalance = INITIAL_METRIC_SUMMARY.totalBalance;
    const adjustedBalance = baseBalance - totalSpent + 400;
    const currentBalance = adjustedBalance > 0 ? adjustedBalance : 45230.5;

    // Calculate effective days in selected range
    let effectiveDays = 1;
    if (
      selectedPresetId !== "all_time" &&
      activePreset?.startDate &&
      activePreset?.endDate
    ) {
      const start = new Date(activePreset.startDate).getTime();
      const end = new Date(activePreset.endDate).getTime();
      effectiveDays = Math.max(
        1,
        Math.ceil(Math.abs(end - start) / (1000 * 60 * 60 * 24)) + 1,
      );
    } else if (dateFilteredTransactions.length > 0) {
      const timestamps = dateFilteredTransactions
        .map((t) => new Date(t.date).getTime())
        .filter((t) => !isNaN(t));
      if (timestamps.length > 0) {
        const minTime = Math.min(...timestamps);
        const maxTime = Math.max(...timestamps);
        effectiveDays = Math.max(
          1,
          Math.ceil((maxTime - minTime) / (1000 * 60 * 60 * 24)) + 1,
        );
      }
    }

    const avgDailySpend =
      dateFilteredTransactions.length > 0 ? totalSpent / effectiveDays : 0;
    const transactionsPerDay =
      dateFilteredTransactions.length > 0
        ? dateFilteredTransactions.length / effectiveDays
        : 0;

    // Calculate Top Category by spending
    const categoryTotals = new Map<string, number>();
    dateFilteredTransactions.forEach((tx) => {
      const cat = tx.category?.trim() || "Uncategorized";
      const spend = Math.abs(tx.amount);
      categoryTotals.set(cat, (categoryTotals.get(cat) || 0) + spend);
    });

    let topCategoryName = "N/A";
    let topCategoryAmount = 0;
    let topCategoryPercentage = 0;

    if (categoryTotals.size > 0 && totalSpent > 0) {
      let maxAmount = 0;
      categoryTotals.forEach((amount, cat) => {
        if (amount > maxAmount) {
          maxAmount = amount;
          topCategoryName = cat;
        }
      });
      topCategoryAmount = maxAmount;
      topCategoryPercentage = (maxAmount / totalSpent) * 100;
    }

    return {
      totalBalance: currentBalance,
      balanceChangePercentage: INITIAL_METRIC_SUMMARY.balanceChangePercentage,
      avgDailySpend,
      transactionsPerDay,
      totalTransactionsCount: dateFilteredTransactions.length,
      daysInRange: effectiveDays,
      topCategoryName,
      topCategoryAmount,
      topCategoryPercentage,
    };
  }, [dateFilteredTransactions, activePreset, selectedPresetId]);

  // CSV Upload handler
  const handleUploadTransactions = async (
    newTransactions: Transaction[],
    replaceExisting: boolean,
    newConnectionId?: number | null,
  ) => {
    if (newConnectionId) {
      await refetchConnections();
      setSelectedConnectionId(newConnectionId);
    } else if (replaceExisting) {
      setTransactions(newTransactions);
    } else {
      const existingIds = new Set(transactions.map((t) => t.id));
      const filteredNew = newTransactions.filter((t) => !existingIds.has(t.id));
      setTransactions([...filteredNew, ...transactions]);
    }

    setSelectedPresetId("all_time");
    setSelectedCategory("all");
    void refetchTransactions();

    const now = new Date();
    const timeStr = now.toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
    setChatMessages((prev) => [
      ...prev,
      {
        id: `msg-${Date.now()}`,
        sender: "assistant",
        text: `Successfully imported ${newTransactions.length} transaction(s). Loaded from database connection.`,
        timestamp: timeStr,
      },
    ]);
  };

  const handleExecuteInsight = (id: string) => {
    setInsights((prev) =>
      prev.map((ins) => (ins.id === id ? { ...ins, status: "executed" } : ins)),
    );

    const targetInsight = insights.find((i) => i.id === id);
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });

    setChatMessages((prev) => [
      ...prev,
      {
        id: `msg-${Date.now()}`,
        sender: "assistant",
        text: `Autonomous rule executed: "${targetInsight?.title}". Funds adjusted in your high-yield reserve.`,
        timestamp: timeStr,
      },
    ]);
  };

  const handleDismissInsight = (id: string) => {
    setInsights((prev) =>
      prev.map((ins) =>
        ins.id === id ? { ...ins, status: "dismissed" } : ins,
      ),
    );
  };

  const handleSendMessage = (text: string) => {
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });

    const userMsg: ChatMessage = {
      id: `msg-user-${Date.now()}`,
      sender: "user",
      text,
      timestamp: timeStr,
    };

    setChatMessages((prev) => [...prev, userMsg]);

    setTimeout(() => {
      let replyText =
        "I've analyzed your portfolio. Your daily spending pace is optimal.";
      const lower = text.toLowerCase();

      if (
        lower.includes("analyze") ||
        lower.includes("expenses") ||
        lower.includes("spend")
      ) {
        const total = dateFilteredTransactions.reduce(
          (acc, t) => acc + Math.abs(t.amount),
          0,
        );
        replyText = `In this period (${activePreset.sublabel}), you have ${dateFilteredTransactions.length} transactions totaling $${total.toFixed(2)} in recorded expenses.`;
      } else if (lower.includes("budget") || lower.includes("recommend")) {
        replyText =
          "Based on your MCC data, limiting Dining Out (MCC 5812) to $50/week will keep your monthly savings rate securely above the 20% goal.";
      } else if (lower.includes("subscription") || lower.includes("audit")) {
        const subs = dateFilteredTransactions.filter((t) =>
          [4899, 5734].includes(t.mcc || 0),
        );
        replyText = `Found ${subs.length} active subscription transactions in this period.`;
      } else if (lower.includes("forecast") || lower.includes("month end")) {
        replyText =
          "Projected month-end balance: $46,420 with an estimated 19.8% final savings rate.";
      } else if (lower.startsWith("search:")) {
        const query = text.replace(/^search:\s*/i, "");
        const matches = dateFilteredTransactions.filter(
          (t) =>
            t.provider_name.toLowerCase().includes(query.toLowerCase()) ||
            t.category.toLowerCase().includes(query.toLowerCase()),
        );
        replyText = `Search found ${matches.length} matching transactions for "${query}".`;
      }

      const aiReply: ChatMessage = {
        id: `msg-ai-${Date.now()}`,
        sender: "assistant",
        text: replyText,
        timestamp: new Date().toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        }),
      };

      setChatMessages((prev) => [...prev, aiReply]);
    }, 600);
  };

  const activeConnection =
    connections.find((c) => c.id === selectedConnectionId) ||
    connections[0] ||
    null;

  const greetingName = "Alex";

  return (
    <div className="bg-[#f7f9fb] text-slate-900 min-h-screen flex flex-col font-sans">
      <Header
        connections={connections}
        activeConnectionId={selectedConnectionId ?? (connections[0]?.id ?? null)}
        onSelectConnection={(connectionId) => {
          setSelectedConnectionId(connectionId);
          setSelectedPresetId("all_time");
          setSelectedCategory("all");
        }}
        loadingConnections={isConnectionsLoading}
        onOpenUpload={() => setIsUploadModalOpen(true)}
        onLogout={logout}
      />

      <main className="flex-1 w-full max-w-[1440px] mx-auto p-4 md:p-8">
        <div className="flex flex-col lg:flex-row gap-6 w-full max-w-full">
          <div className="flex-1 min-w-0 flex flex-col gap-6">
            {/* Header Greeting & Date Selector */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                  Good morning, {greetingName}! <span className="animate-bounce">👋</span>
                </h2>
                <p className="text-sm text-slate-500 mt-0.5">
                  Here's your financial overview for{" "}
                  <strong className="text-slate-700 font-semibold">
                    {activeConnection ? activeConnection.name : "Active Dataset"}
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

            <div className="flex items-center justify-between gap-2 pt-1">
              <CategoryFilters
                selectedCategory={selectedCategory}
                onSelectCategory={setSelectedCategory}
              />
            </div>

            <SpendingTrendChart
              transactions={dateFilteredTransactions}
              selectedCategory={selectedCategory}
            />

            <SpendingByCategoryChart
              categorySummary={categorySummary}
              loading={isCategoryLoading}
              selectedCategory={selectedCategory}
              onSelectCategory={(category) => setSelectedCategory(category)}
            />
            <TransactionsTable
              transactions={dateFilteredTransactions}
              selectedCategory={selectedCategory}
              onOpenUpload={() => setIsUploadModalOpen(true)}
            />
          </div>

          <CopilotPanel
            insights={insights}
            messages={chatMessages}
            onExecuteInsight={handleExecuteInsight}
            onDismissInsight={handleDismissInsight}
            onSendMessage={handleSendMessage}
          />
        </div>
      </main>

      <UploadModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        onUploadTransactions={handleUploadTransactions}
      />
    </div>
  );
};
