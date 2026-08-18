import React, { useState, useMemo } from 'react';
import type { Transaction } from '../types/transaction';
import type { MetricSummary } from '../types/metrics';
import type { Insight, ChatMessage } from '../types/copilot';

import { Header } from './common/Header';
import { UploadModal } from './common/UploadModal';
import { MetricCards } from './dashboard/MetricCards';
import { CategoryFilters } from './dashboard/CategoryFilters';
import { SpendingTrendChart } from './dashboard/SpendingTrendChart';
import { SpendingByCategoryChart } from './dashboard/SpendingByCategoryChart';
import { TransactionsTable } from './dashboard/TransactionsTable';
import { CopilotPanel } from './copilot/CopilotPanel';

import { INITIAL_TRANSACTIONS } from '../data/mockTransactions';
import { INITIAL_METRIC_SUMMARY } from '../data/mockMetrics';
import { INITIAL_INSIGHTS, INITIAL_CHAT_MESSAGES } from '../data/mockInsights';
import { getCategoryFromMCC } from '../utils/mccHelper';
import {
  calculateDateRangeLimits,
  generateDatePresets,
  isDateWithinRange,
  type DatePresetId,
} from '../utils/dateHelper';

interface HomeScreenProps {
  initialTransactions?: Transaction[];
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  initialTransactions = INITIAL_TRANSACTIONS,
}) => {
  // State for Transactions
  const [transactions, setTransactions] = useState<Transaction[]>(initialTransactions);

  // Dynamic Date Limits and Presets
  const dateLimits = useMemo(() => calculateDateRangeLimits(transactions), [transactions]);
  const datePresets = useMemo(() => generateDatePresets(dateLimits), [dateLimits]);

  // Selected Date Preset
  const [selectedPresetId, setSelectedPresetId] = useState<DatePresetId>('all_time');
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);

  // Active Date Preset
  const activePreset = useMemo(() => {
    return datePresets.find((p) => p.id === selectedPresetId) || datePresets[0];
  }, [datePresets, selectedPresetId]);

  // State for Category Filter
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  // Filter transactions within the selected date range
  const dateFilteredTransactions = useMemo(() => {
    return transactions.filter((tx) =>
      isDateWithinRange(tx.date, activePreset.startDate, activePreset.endDate)
    );
  }, [transactions, activePreset]);

  // State for CSV Upload Modal
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);

  // State for Copilot
  const [insights, setInsights] = useState<Insight[]>(INITIAL_INSIGHTS);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>(INITIAL_CHAT_MESSAGES);

  // Dynamically compute metrics based on date-filtered transactions
  const metrics: MetricSummary = useMemo(() => {
    // Total spent (absolute value of negative transactions or positive expenses)
    const totalSpent = dateFilteredTransactions
      .reduce((sum, t) => sum + Math.abs(t.amount), 0);

    const baseBalance = INITIAL_METRIC_SUMMARY.totalBalance;
    // Adjust balance based on expenses
    const adjustedBalance = baseBalance - totalSpent + 400;

    return {
      ...INITIAL_METRIC_SUMMARY,
      totalBalance: adjustedBalance > 0 ? adjustedBalance : 45230.50,
      activeAiTasksCount: insights.filter((i) => i.status === 'active').length,
    };
  }, [dateFilteredTransactions, insights]);

  // Handle CSV Upload / Append
  const handleUploadTransactions = (newTransactions: Transaction[], replaceExisting: boolean) => {
    if (replaceExisting) {
      setTransactions(newTransactions);
    } else {
      // Merge while avoiding duplicate IDs
      const existingIds = new Set(transactions.map((t) => t.id));
      const filteredNew = newTransactions.filter((t) => !existingIds.has(t.id));
      setTransactions([...filteredNew, ...transactions]);
    }

    // Default to 'all_time' when new dataset is imported to show the full range
    setSelectedPresetId('all_time');

    // Add Copilot notification message
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    setChatMessages((prev) => [
      ...prev,
      {
        id: `msg-${Date.now()}`,
        sender: 'assistant',
        text: `Successfully imported ${newTransactions.length} transaction(s). Date boundaries and MCC categorization updated automatically.`,
        timestamp: timeStr,
      },
    ]);
  };

  // Handle Insight Actions
  const handleExecuteInsight = (id: string) => {
    setInsights((prev) =>
      prev.map((ins) => (ins.id === id ? { ...ins, status: 'executed' } : ins))
    );

    const targetInsight = insights.find((i) => i.id === id);
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    setChatMessages((prev) => [
      ...prev,
      {
        id: `msg-${Date.now()}`,
        sender: 'assistant',
        text: `Autonomous rule executed: "${targetInsight?.title}". Funds adjusted in your high-yield reserve.`,
        timestamp: timeStr,
      },
    ]);
  };

  const handleDismissInsight = (id: string) => {
    setInsights((prev) =>
      prev.map((ins) => (ins.id === id ? { ...ins, status: 'dismissed' } : ins))
    );
  };

  // Handle Copilot Chat Message
  const handleSendMessage = (text: string) => {
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    // Add user message
    const userMsg: ChatMessage = {
      id: `msg-user-${Date.now()}`,
      sender: 'user',
      text,
      timestamp: timeStr,
    };

    setChatMessages((prev) => [...prev, userMsg]);

    // Simulate smart AI response based on query
    setTimeout(() => {
      let replyText = "I've analyzed your portfolio. Your daily spending pace is optimal.";
      const lower = text.toLowerCase();

      if (lower.includes('analyze') || lower.includes('expenses') || lower.includes('spend')) {
        const total = dateFilteredTransactions.reduce((acc, t) => acc + Math.abs(t.amount), 0);
        replyText = `In this period (${activePreset.sublabel}), you have ${dateFilteredTransactions.length} transactions totaling $${total.toFixed(2)} in recorded expenses.`;
      } else if (lower.includes('budget') || lower.includes('recommend')) {
        replyText = "Based on your MCC data, limiting Dining Out (MCC 5812) to $50/week will keep your monthly savings rate securely above the 20% goal.";
      } else if (lower.includes('subscription') || lower.includes('audit')) {
        const subs = dateFilteredTransactions.filter((t) => ['4899', '5734'].includes(t.mcc));
        replyText = `Found ${subs.length} active subscription transactions in this period.`;
      } else if (lower.includes('forecast') || lower.includes('month end')) {
        replyText = "Projected month-end balance: $46,420 with an estimated 19.8% final savings rate.";
      } else if (lower.startsWith('search:')) {
        const query = text.replace(/^search:\s*/i, '');
        const matches = dateFilteredTransactions.filter((t) =>
          t.provider_name.toLowerCase().includes(query.toLowerCase()) ||
          getCategoryFromMCC(t.mcc).category.toLowerCase().includes(query.toLowerCase())
        );
        replyText = `Search found ${matches.length} matching transactions for "${query}".`;
      }

      const aiReply: ChatMessage = {
        id: `msg-ai-${Date.now()}`,
        sender: 'assistant',
        text: replyText,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      };

      setChatMessages((prev) => [...prev, aiReply]);
    }, 600);
  };

  return (
    <div className="bg-[#f7f9fb] text-slate-900 min-h-screen flex flex-col font-sans">
      {/* Top Header */}
      <Header onOpenUpload={() => setIsUploadModalOpen(true)} />

      {/* Main Content Area */}
      <main className="flex-1 w-full max-w-[1440px] mx-auto p-4 md:p-8">
        <div className="flex flex-col lg:flex-row gap-6 w-full max-w-full">
          {/* Main Dashboard Canvas (Left / Center) */}
          <div className="flex-1 min-w-0 flex flex-col gap-6">
            {/* Header Greeting & Date Selector */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                  Good morning, Alex! <span className="animate-bounce">👋</span>
                </h2>
                <p className="text-sm text-slate-500 mt-0.5">
                  Here's your financial overview for{' '}
                  <span className="font-semibold text-slate-700">{activePreset.sublabel}</span>
                </p>
              </div>

              {/* Date Presets and Filters */}
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
                          ? 'bg-emerald-50 text-emerald-900 font-semibold'
                          : 'text-slate-700 hover:bg-slate-50'
                          }`}
                      >
                        <div>
                          <p className="font-medium text-slate-900">{preset.label}</p>
                          <p className="text-[11px] text-slate-500 font-mono">{preset.sublabel}</p>
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

            {/* 1. Metric Summary Cards */}
            <MetricCards metrics={metrics} />

            {/* 2. Category Filter Pills */}
            <div className="flex items-center justify-between gap-2 pt-1">
              <CategoryFilters
                selectedCategory={selectedCategory}
                onSelectCategory={setSelectedCategory}
              />
            </div>

            {/* 3. Analytics: Spending Trend Chart */}
            <SpendingTrendChart
              transactions={dateFilteredTransactions}
              selectedCategory={selectedCategory}
            />

            {/* 4. Analytics: Spending by Category Chart */}
            <SpendingByCategoryChart
              transactions={dateFilteredTransactions}
              selectedCategory={selectedCategory}
              onSelectCategory={setSelectedCategory}
            />

            {/* 5. Transactions Data Grid */}
            <TransactionsTable
              transactions={dateFilteredTransactions}
              selectedCategory={selectedCategory}
              onOpenUpload={() => setIsUploadModalOpen(true)}
            />
          </div>

          {/* Right Rail: Mush Copilot */}
          <CopilotPanel
            insights={insights}
            messages={chatMessages}
            onExecuteInsight={handleExecuteInsight}
            onDismissInsight={handleDismissInsight}
            onSendMessage={handleSendMessage}
          />
        </div>
      </main>

      {/* CSV Dataset Upload Modal */}
      <UploadModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        //        onUploadTransactions={(newTx) => handleUploadTransactions(newTx, true)}
        onUploadTransactions={handleUploadTransactions}
      />
    </div>
  );
};
