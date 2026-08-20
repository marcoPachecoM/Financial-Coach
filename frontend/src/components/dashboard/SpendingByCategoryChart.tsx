import React, { useMemo, useState } from 'react';
import type { CategorySummaryItem } from '../../types/transaction';
import { formatCurrency } from '../../utils/formatters';

interface CategoryColorConfig {
  hex: string;
  bg: string;
  text: string;
  badgeBg: string;
}

const CATEGORY_COLOR_MAP: Record<string, CategoryColorConfig> = {
  Shopping: { hex: '#ec4899', bg: 'bg-pink-500', text: 'text-pink-700', badgeBg: 'bg-pink-50 border-pink-200' },
  Insurance: { hex: '#6366f1', bg: 'bg-indigo-500', text: 'text-indigo-700', badgeBg: 'bg-indigo-50 border-indigo-200' },
  'Automotive & Fuel': { hex: '#ef4444', bg: 'bg-red-500', text: 'text-red-700', badgeBg: 'bg-red-50 border-red-200' },
  'Food & Dining': { hex: '#f97316', bg: 'bg-orange-500', text: 'text-orange-700', badgeBg: 'bg-orange-50 border-orange-200' },
  Healthcare: { hex: '#14b8a6', bg: 'bg-teal-500', text: 'text-teal-700', badgeBg: 'bg-teal-50 border-teal-200' },
  Utilities: { hex: '#06b6d4', bg: 'bg-cyan-500', text: 'text-cyan-700', badgeBg: 'bg-cyan-50 border-cyan-200' },
  Other: { hex: '#94a3b8', bg: 'bg-slate-400', text: 'text-slate-600', badgeBg: 'bg-slate-100 border-slate-200' },
  Transport: { hex: '#f59e0b', bg: 'bg-amber-500', text: 'text-amber-700', badgeBg: 'bg-amber-50 border-amber-200' },
  'Home & Hardware': { hex: '#a855f7', bg: 'bg-purple-500', text: 'text-purple-700', badgeBg: 'bg-purple-50 border-purple-200' },
  Telecom: { hex: '#0ea5e9', bg: 'bg-sky-500', text: 'text-sky-700', badgeBg: 'bg-sky-50 border-sky-200' },
  Entertainment: { hex: '#8b5cf6', bg: 'bg-purple-500', text: 'text-purple-700', badgeBg: 'bg-purple-50 border-purple-200' },
  'Personal & Business': { hex: '#f43f5e', bg: 'bg-rose-500', text: 'text-rose-700', badgeBg: 'bg-rose-50 border-rose-200' },
  Government: { hex: '#64748b', bg: 'bg-slate-500', text: 'text-slate-700', badgeBg: 'bg-slate-100 border-slate-200' },
};

const DEFAULT_CATEGORY_COLOR: CategoryColorConfig = {
  hex: '#64748b',
  bg: 'bg-slate-500',
  text: 'text-slate-700',
  badgeBg: 'bg-slate-100 border-slate-200',
};

interface CategoryChartItem {
  category: string;
  total: number;
  count: number;
  percentage: number;
  icon: string;
  colorConfig: CategoryColorConfig;
}

export interface SpendingByCategoryChartProps {
  categorySummary?: CategorySummaryItem[];
  loading?: boolean;
  selectedCategory?: string;
  onSelectCategory?: (category: string) => void;
}

export const SpendingByCategoryChart: React.FC<SpendingByCategoryChartProps> = ({
  categorySummary = [],
  loading = false,
  selectedCategory = 'all',
  onSelectCategory,
}) => {
  const [hoveredCategory, setHoveredCategory] = useState<string | null>(null);

  const categoryList = useMemo<CategoryChartItem[]>(() => {
    return (categorySummary || []).map((item) => ({
      category: item.category,
      total: Number(item.amount) || 0,
      count: Number(item.count) || 0,
      percentage: Number(item.percentage) || 0,
      icon: 'payments',
      colorConfig: CATEGORY_COLOR_MAP[item.category] ?? DEFAULT_CATEGORY_COLOR,
    }));
  }, [categorySummary]);

  const totalSpending = useMemo(() => {
    return categoryList.reduce((sum, item) => sum + item.total, 0);
  }, [categoryList]);

  const totalTransactionsCount = useMemo(() => {
    return categoryList.reduce((sum, item) => sum + item.count, 0);
  }, [categoryList]);

  const radius = 64;
  const circumference = 2 * Math.PI * radius;

  const donutSegments = useMemo(() => {
    let offset = 0;

    return categoryList.map((item) => {
      const segmentLength = (item.percentage / 100) * circumference;
      const strokeDashoffset = -offset;
      offset += segmentLength;

      const gap = categoryList.length > 1 ? 2.5 : 0;
      const dashLength = Math.max(0, segmentLength - gap);

      return {
        ...item,
        dashLength,
        strokeDashoffset,
      };
    });
  }, [categoryList, circumference]);

  const activeItem = useMemo(() => {
    if (hoveredCategory) {
      return categoryList.find((item) => item.category === hoveredCategory) ?? null;
    }
    if (selectedCategory !== 'all') {
      return (
        categoryList.find(
          (item) => item.category.toLowerCase() === selectedCategory.toLowerCase()
        ) ?? null
      );
    }
    return null;
  }, [hoveredCategory, selectedCategory, categoryList]);

  if (loading) {
    return (
      <section className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow w-full">
        <div className="flex items-center justify-between pb-4 mb-6 border-b border-slate-100">
          <div>
            <h3 className="text-lg font-bold text-slate-900">Spending by Category</h3>
            <p className="text-xs text-slate-500 mt-0.5">See where your money went</p>
          </div>
          <div className="animate-pulse">
            <div className="h-6 w-24 bg-slate-100 rounded" />
          </div>
        </div>
        <div className="h-56 flex items-center justify-center">
          <span className="text-sm text-slate-400">Loading spending data...</span>
        </div>
      </section>
    );
  }

  return (
    <section className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow w-full max-w-full overflow-hidden">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 mb-6 border-b border-slate-100 gap-2">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-lg font-bold text-slate-900 font-sans">Spending by Category</h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
              Breakdown
            </span>

            {selectedCategory !== 'all' && (
              <span className="text-[11px] px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-medium inline-flex items-center gap-1 border border-emerald-200">
                Filtered: {selectedCategory}
                {onSelectCategory && (
                  <button
                    onClick={() => onSelectCategory('all')}
                    className="hover:text-emerald-900 font-bold ml-0.5 cursor-pointer"
                    title="Clear category filter"
                  >
                    ×
                  </button>
                )}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">See where your money went</p>
        </div>

        <div className="text-left sm:text-right">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
            Categorized Total
          </p>
          <h4 className="text-xl font-bold text-slate-900 font-sans">
            {formatCurrency(totalSpending)}
          </h4>
        </div>
      </div>

      {/* Empty state */}
      {categoryList.length === 0 ? (
        <div className="h-56 flex flex-col items-center justify-center text-slate-400">
          <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">
            donut_large
          </span>
          <p className="text-sm">No spending records found for this period.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          {/* Donut Chart */}
          <div className="lg:col-span-5 flex flex-col items-center justify-center relative py-2">
            <div className="relative w-52 h-52 sm:w-60 sm:h-60 flex items-center justify-center">
              <svg
                className="w-full h-full transform -rotate-90"
                viewBox="0 0 160 160"
                aria-label="Spending by category donut chart"
              >
                <circle
                  cx="80"
                  cy="80"
                  r={radius}
                  fill="transparent"
                  stroke="#f1f5f9"
                  strokeWidth="18"
                />

                {donutSegments.map((segment) => {
                  const isSelected =
                    selectedCategory.toLowerCase() === segment.category.toLowerCase();
                  const isHovered =
                    hoveredCategory === segment.category || (!hoveredCategory && isSelected);
                  const isDimmed =
                    (hoveredCategory && hoveredCategory !== segment.category) ||
                    (!hoveredCategory && selectedCategory !== 'all' && !isSelected);

                  return (
                    <circle
                      key={segment.category}
                      cx="80"
                      cy="80"
                      r={radius}
                      fill="transparent"
                      stroke={segment.colorConfig.hex}
                      strokeWidth={isHovered ? 24 : 18}
                      strokeDasharray={`${segment.dashLength} ${circumference}`}
                      strokeDashoffset={segment.strokeDashoffset}
                      strokeLinecap="round"
                      className="transition-all duration-300 cursor-pointer"
                      style={{
                        opacity: isDimmed ? 0.35 : 1,
                        filter: isHovered
                          ? 'drop-shadow(0 2px 6px rgba(0,0,0,0.15))'
                          : 'none',
                      }}
                      onMouseEnter={() => setHoveredCategory(segment.category)}
                      onMouseLeave={() => setHoveredCategory(null)}
                      onClick={() =>
                        onSelectCategory?.(isSelected ? 'all' : segment.category)
                      }
                    />
                  );
                })}
              </svg>

              {/* Center readout */}
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none text-center px-4">
                {activeItem ? (
                  <div className="flex flex-col items-center animate-in fade-in zoom-in-95 duration-150">
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center mb-1 border ${activeItem.colorConfig.badgeBg}`}
                    >
                      <span
                        className={`material-symbols-outlined !text-[16px] ${activeItem.colorConfig.text}`}
                      >
                        {activeItem.icon}
                      </span>
                    </div>
                    <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider font-mono truncate max-w-[110px]">
                      {activeItem.category}
                    </span>
                    <span className="text-lg sm:text-xl font-bold text-slate-900 font-sans tracking-tight">
                      {formatCurrency(activeItem.total)}
                    </span>
                    <span className="text-xs font-bold font-mono text-emerald-600">
                      {activeItem.percentage.toFixed(1)}%
                    </span>
                  </div>
                ) : (
                  <div className="flex flex-col items-center animate-in fade-in duration-150">
                    <span className="text-[10px] sm:text-[11px] font-semibold uppercase tracking-wider text-slate-400 font-mono">
                      Total Spent
                    </span>
                    <span className="text-lg sm:text-xl font-bold text-slate-900 font-sans tracking-tight">
                      {formatCurrency(totalSpending)}
                    </span>
                    <span className="text-[11px] text-slate-500 font-mono mt-0.5">
                      {categoryList.length} categories · {totalTransactionsCount} tx
                    </span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Category List Details */}
          <div className="lg:col-span-7 flex flex-col gap-2.5">
            <div className="flex items-center justify-between px-1 text-[11px] font-mono font-semibold uppercase text-slate-400">
              <span>Category</span>
              <span>Amount · Share</span>
            </div>

            <div className="flex flex-col gap-1.5 max-h-[290px] overflow-y-auto pr-1">
              {categoryList.map((item) => {
                const isSelected =
                  selectedCategory.toLowerCase() === item.category.toLowerCase();
                const isHovered =
                  hoveredCategory === item.category || (!hoveredCategory && isSelected);
                const isDimmed =
                  (hoveredCategory && hoveredCategory !== item.category) ||
                  (!hoveredCategory && selectedCategory !== 'all' && !isSelected);

                return (
                  <div
                    key={item.category}
                    onMouseEnter={() => setHoveredCategory(item.category)}
                    onMouseLeave={() => setHoveredCategory(null)}
                    onClick={() =>
                      onSelectCategory?.(isSelected ? 'all' : item.category)
                    }
                    className={`p-2.5 rounded-xl border transition-all duration-150 cursor-pointer ${
                      isHovered
                        ? 'bg-slate-50/90 border-slate-300 shadow-xs'
                        : 'border-slate-100 hover:bg-slate-50/60'
                    }`}
                    style={{ opacity: isDimmed ? 0.45 : 1 }}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div
                          className={`w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 border ${item.colorConfig.badgeBg}`}
                        >
                          <span
                            className={`material-symbols-outlined !text-[16px] ${item.colorConfig.text}`}
                          >
                            {item.icon}
                          </span>
                        </div>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-slate-800 font-sans truncate">
                            {item.category}
                          </p>
                          <p className="text-[11px] text-slate-400 font-mono">
                            {item.count} {item.count === 1 ? 'transaction' : 'transactions'}
                          </p>
                        </div>
                      </div>

                      <div className="text-right flex-shrink-0 flex items-center gap-2">
                        <span className="text-xs sm:text-sm font-bold text-slate-900 font-sans">
                          {formatCurrency(item.total)}
                        </span>
                        <span className="text-[11px] font-mono font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 min-w-[48px] text-center">
                          {item.percentage.toFixed(1)}%
                        </span>
                      </div>
                    </div>

                    <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden mt-2">
                      <div
                        className="h-full rounded-full transition-all duration-300"
                        style={{
                          width: `${Math.min(100, item.percentage)}%`,
                          backgroundColor: item.colorConfig.hex,
                        }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </section>
  );
};