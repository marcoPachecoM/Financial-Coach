import React, { useState, useMemo } from 'react';
import type { Transaction } from '../../types/transaction';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { getCategoryFromMCC } from '../../utils/mccHelper';

interface SpendingTrendChartProps {
  transactions: Transaction[];
  selectedCategory: string;
}

export const SpendingTrendChart: React.FC<SpendingTrendChartProps> = ({
  transactions,
  selectedCategory,
}) => {
  const [hoveredDay, setHoveredDay] = useState<{
    date: string;
    total: number;
    count: number;
  } | null>(null);

  // Group transactions by date (normalized to YYYY-MM-DD dayKey)
  const chartData = useMemo(() => {
    // Filter by category if selected
    const filtered = transactions.filter((tx) => {
      if (selectedCategory === 'all') return true;
      const cat = getCategoryFromMCC(tx.mcc).category;
      return cat.toLowerCase() === selectedCategory.toLowerCase();
    });

    // Extract unique dates sorted ascending
    const dateMap = new Map<string, { total: number; count: number }>();

    // Sort transactions chronologically by normalized day
    const sortedTx = [...filtered].sort((a, b) => a.date.localeCompare(b.date));

    sortedTx.forEach((tx) => {
      const dayKey = tx.date.trim().split(/[\sT]/)[0];
      // Treat negative amounts as positive spending for the chart
      const expense = Math.abs(tx.amount);
      const current = dateMap.get(dayKey) || { total: 0, count: 0 };
      dateMap.set(dayKey, {
        total: current.total + expense,
        count: current.count + 1,
      });
    });

    const dataList = Array.from(dateMap.entries()).map(([date, val]) => ({
      date,
      formattedDate: formatDate(date),
      total: val.total,
      count: val.count,
    }));

    return dataList;
  }, [transactions, selectedCategory]);

  const totalPeriodSpending = useMemo(() => {
    return chartData.reduce((acc, curr) => acc + curr.total, 0);
  }, [chartData]);

  const maxDailySpend = useMemo(() => {
    const max = Math.max(...chartData.map((d) => d.total), 10);
    return max * 1.15; // 15% headroom
  }, [chartData]);

  // Determine if horizontal scrolling is needed for large datasets
  const hasManyItems = chartData.length > 10;
  const innerWidthStyle = hasManyItems
    ? { minWidth: `${Math.max(540, chartData.length * 44)}px` }
    : { width: '100%' };

  return (
    <section className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow w-full max-w-full overflow-hidden">
      {/* Chart Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 mb-6 border-b border-slate-100 gap-2">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-lg font-bold text-slate-900 font-sans">Spending Trend</h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
              Daily Breakdown
            </span>
            {hasManyItems && (
              <span className="text-[11px] px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-medium inline-flex items-center gap-1">
                <span className="material-symbols-outlined !text-[13px]">swap_horiz</span>
                Scrollable ({chartData.length} days)
              </span>
            )}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            {selectedCategory === 'all'
              ? 'Your spending across all categories for the selected period'
              : `Filtered by: ${selectedCategory}`}
          </p>
        </div>

        <div className="text-left sm:text-right">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
            Period Total
          </p>
          <h4 className="text-xl font-bold text-slate-900 font-sans">
            {formatCurrency(totalPeriodSpending)}
          </h4>
        </div>
      </div>

      {/* Bar Chart Area with Strict Responsive Container & Card-Bounded Scroll */}
      {chartData.length === 0 ? (
        <div className="h-56 flex flex-col items-center justify-center text-slate-400">
          <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">bar_chart</span>
          <p className="text-sm">No spending records found for this category and date range.</p>
        </div>
      ) : (
        <div className="w-full max-w-full overflow-x-auto overflow-y-hidden pb-2 focus:outline-hidden">
          <div className="relative pt-6 pb-1" style={innerWidthStyle}>
            {/* Hover Tooltip Popup */}
            {hoveredDay && (
              <div className="absolute top-0 right-4 bg-slate-900 text-white text-xs px-3 py-1.5 rounded-lg shadow-lg z-20 pointer-events-none flex items-center gap-2 font-mono animate-in fade-in duration-100">
                <span className="font-semibold">{formatDate(hoveredDay.date)}:</span>
                <span className="text-emerald-400 font-bold">{formatCurrency(hoveredDay.total)}</span>
                <span className="text-slate-400">({hoveredDay.count} tx)</span>
              </div>
            )}

            {/* Grid lines */}
            <div className="h-48 w-full relative flex items-end justify-between gap-2 sm:gap-4 z-10 px-2">
              <div className="absolute inset-x-0 bottom-0 border-t border-slate-200/80" />
              <div className="absolute inset-x-0 bottom-1/4 border-t border-dashed border-slate-200/60" />
              <div className="absolute inset-x-0 bottom-2/4 border-t border-dashed border-slate-200/60" />
              <div className="absolute inset-x-0 bottom-3/4 border-t border-dashed border-slate-200/60" />

              {/* Bars */}
              {chartData.map((item, index) => {
                const heightPercent = Math.max(8, (item.total / maxDailySpend) * 100);
                const isHovered = hoveredDay?.date === item.date;

                return (
                  <div
                    key={item.date || index}
                    className="flex-1 min-w-[28px] flex flex-col items-center h-full justify-end group cursor-pointer relative"
                    onMouseEnter={() => setHoveredDay(item)}
                    onMouseLeave={() => setHoveredDay(null)}
                  >
                    <div
                      className={`w-full max-w-[48px] rounded-t-lg transition-all duration-200 ${
                        isHovered
                          ? 'bg-emerald-500 shadow-md shadow-emerald-200'
                          : 'bg-emerald-100 group-hover:bg-emerald-300'
                      }`}
                      style={{ height: `${heightPercent}%` }}
                    />
                  </div>
                );
              })}
            </div>

            {/* X-Axis Labels */}
            <div className="flex justify-between mt-3 text-xs font-mono text-slate-500 px-2">
              {chartData.map((item) => (
                <span
                  key={item.date}
                  className="truncate text-center flex-1 min-w-[28px] px-0.5 text-[11px]"
                  title={item.formattedDate}
                >
                  {item.formattedDate}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
