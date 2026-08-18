import React, { useState, useMemo } from 'react';
import type { Transaction } from '../../types/transaction';
import { formatCurrency } from '../../utils/formatters';
import { getCategoryFromMCC } from '../../utils/mccHelper';

interface CategoryColorConfig {
  hex: string;
  bg: string;
  text: string;
  badgeBg: string;
}

const CATEGORY_COLOR_MAP: Record<string, CategoryColorConfig> = {
  Groceries: {
    hex: '#10b981',
    bg: 'bg-emerald-500',
    text: 'text-emerald-700',
    badgeBg: 'bg-emerald-50 border-emerald-200',
  },
  'Food & Dining': {
    hex: '#f97316',
    bg: 'bg-orange-500',
    text: 'text-orange-700',
    badgeBg: 'bg-orange-50 border-orange-200',
  },
  Entertainment: {
    hex: '#8b5cf6',
    bg: 'bg-purple-500',
    text: 'text-purple-700',
    badgeBg: 'bg-purple-50 border-purple-200',
  },
  Subscriptions: {
    hex: '#3b82f6',
    bg: 'bg-blue-500',
    text: 'text-blue-700',
    badgeBg: 'bg-blue-50 border-blue-200',
  },
  Transport: {
    hex: '#f59e0b',
    bg: 'bg-amber-500',
    text: 'text-amber-700',
    badgeBg: 'bg-amber-50 border-amber-200',
  },
  Utilities: {
    hex: '#06b6d4',
    bg: 'bg-cyan-500',
    text: 'text-cyan-700',
    badgeBg: 'bg-cyan-50 border-cyan-200',
  },
  Shopping: {
    hex: '#ec4899',
    bg: 'bg-pink-500',
    text: 'text-pink-700',
    badgeBg: 'bg-pink-50 border-pink-200',
  },
  General: {
    hex: '#64748b',
    bg: 'bg-slate-500',
    text: 'text-slate-700',
    badgeBg: 'bg-slate-100 border-slate-200',
  },
};

const DEFAULT_CATEGORY_COLOR: CategoryColorConfig = {
  hex: '#64748b',
  bg: 'bg-slate-500',
  text: 'text-slate-700',
  badgeBg: 'bg-slate-100 border-slate-200',
};

export interface SpendingByCategoryChartProps {
  transactions: Transaction[];
  selectedCategory?: string;
  onSelectCategory?: (category: string) => void;
}

export const SpendingByCategoryChart: React.FC<SpendingByCategoryChartProps> = ({
  transactions,
  selectedCategory = 'all',
  onSelectCategory,
}) => {
  const [hoveredCategory, setHoveredCategory] = useState<string | null>(null);

  // Group and aggregate transactions by category
  const { categoryList, totalSpending, totalTransactionsCount } = useMemo(() => {
    const categoryMap = new Map<
      string,
      {
        category: string;
        total: number;
        count: number;
        icon: string;
        colorConfig: CategoryColorConfig;
      }
    >();

    transactions.forEach((tx) => {
      const mccInfo = getCategoryFromMCC(tx.mcc);
      const cat = mccInfo.category;
      const expense = Math.abs(tx.amount);
      const colorConfig = CATEGORY_COLOR_MAP[cat] || DEFAULT_CATEGORY_COLOR;

      const existing = categoryMap.get(cat) || {
        category: cat,
        total: 0,
        count: 0,
        icon: mccInfo.icon,
        colorConfig,
      };

      categoryMap.set(cat, {
        ...existing,
        total: existing.total + expense,
        count: existing.count + 1,
      });
    });

    const total = Array.from(categoryMap.values()).reduce((sum, item) => sum + item.total, 0);

    const list = Array.from(categoryMap.values())
      .map((item) => ({
        ...item,
        percentage: total > 0 ? (item.total / total) * 100 : 0,
      }))
      .sort((a, b) => b.total - a.total);

    return {
      categoryList: list,
      totalSpending: total,
      totalTransactionsCount: transactions.length,
    };
  }, [transactions]);

  // Donut chart geometry settings
  const radius = 64;
  const circumference = 2 * Math.PI * radius; // ~402.12

  // Compute SVG segment offsets
  const donutSegments = useMemo(() => {
    let offset = 0;
    const segments = [];
    for (const item of categoryList) {
      const segmentLength = (item.percentage / 100) * circumference;
      const strokeDashoffset = -offset;
      offset += segmentLength;

      // Small gap between slices if more than 1 category
      const gap = categoryList.length > 1 ? 2.5 : 0;
      const dashLength = Math.max(0, segmentLength - gap);

      segments.push({
        ...item,
        dashLength,
        strokeDashoffset,
      });
    }
    return segments;
  }, [categoryList, circumference]);

  // Active category to display in the donut hole
  const activeItem = useMemo(() => {
    if (hoveredCategory) {
      return categoryList.find((c) => c.category === hoveredCategory) || null;
    }
    if (selectedCategory && selectedCategory !== 'all') {
      return (
        categoryList.find(
          (c) => c.category.toLowerCase() === selectedCategory.toLowerCase()
        ) || null
      );
    }
    return null;
  }, [hoveredCategory, selectedCategory, categoryList]);

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

      {/* Content Area */}
      {categoryList.length === 0 ? (
        <div className="h-56 flex flex-col items-center justify-center text-slate-400">
          <span className="material-symbols-outlined !text-[36px] text-slate-300 mb-2">
            donut_large
          </span>
          <p className="text-sm">No spending records found for this period.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          {/* Left: Donut Chart Visual */}
          <div className="lg:col-span-5 flex flex-col items-center justify-center relative py-2">
            <div className="relative w-52 h-52 sm:w-60 sm:h-60 flex items-center justify-center">
              <svg
                className="w-full h-full transform -rotate-90"
                viewBox="0 0 160 160"
                aria-label="Spending by category donut chart"
              >
                {/* Background Ring */}
                <circle
                  cx="80"
                  cy="80"
                  r={radius}
                  fill="transparent"
                  stroke="#f1f5f9"
                  strokeWidth="18"
                />

                {/* Category Donut Segments */}
                {donutSegments.map((segment) => {
                  const isHovered =
                    hoveredCategory === segment.category ||
                    (!hoveredCategory &&
                      selectedCategory.toLowerCase() === segment.category.toLowerCase());
                  const isDimmed =
                    (hoveredCategory && hoveredCategory !== segment.category) ||
                    (!hoveredCategory &&
                      selectedCategory !== 'all' &&
                      selectedCategory.toLowerCase() !== segment.category.toLowerCase());

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
                        filter: isHovered ? 'drop-shadow(0 2px 6px rgba(0,0,0,0.15))' : 'none',
                      }}
                      onMouseEnter={() => setHoveredCategory(segment.category)}
                      onMouseLeave={() => setHoveredCategory(null)}
                      onClick={() =>
                        onSelectCategory?.(
                          selectedCategory.toLowerCase() === segment.category.toLowerCase()
                            ? 'all'
                            : segment.category
                        )
                      }
                    />
                  );
                })}
              </svg>

              {/* Donut Hollow Center Info */}
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

            <p className="text-[11px] text-slate-400 mt-2 text-center font-sans hidden sm:block">
              Hover or click slices to inspect
            </p>
          </div>

          {/* Right: Structured Data Legend */}
          <div className="lg:col-span-7 flex flex-col gap-2.5">
            <div className="flex items-center justify-between px-1 text-[11px] font-mono font-semibold uppercase text-slate-400">
              <span>Category</span>
              <span>Amount · Share</span>
            </div>

            <div className="flex flex-col gap-1.5 max-h-[290px] overflow-y-auto pr-1">
              {categoryList.map((item) => {
                const isHovered =
                  hoveredCategory === item.category ||
                  (!hoveredCategory &&
                    selectedCategory.toLowerCase() === item.category.toLowerCase());
                const isDimmed =
                  (hoveredCategory && hoveredCategory !== item.category) ||
                  (!hoveredCategory &&
                    selectedCategory !== 'all' &&
                    selectedCategory.toLowerCase() !== item.category.toLowerCase());

                return (
                  <div
                    key={item.category}
                    onMouseEnter={() => setHoveredCategory(item.category)}
                    onMouseLeave={() => setHoveredCategory(null)}
                    onClick={() =>
                      onSelectCategory?.(
                        selectedCategory.toLowerCase() === item.category.toLowerCase()
                          ? 'all'
                          : item.category
                      )
                    }
                    className={`p-2.5 rounded-xl border transition-all duration-150 cursor-pointer ${
                      isHovered
                        ? 'bg-slate-50/90 border-slate-300 shadow-xs'
                        : 'border-slate-100 hover:bg-slate-50/60'
                    }`}
                    style={{ opacity: isDimmed ? 0.45 : 1 }}
                  >
                    <div className="flex items-center justify-between gap-3">
                      {/* Category Icon & Label */}
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

                      {/* Amount & Percentage */}
                      <div className="text-right flex-shrink-0 flex items-center gap-2">
                        <span className="text-xs sm:text-sm font-bold text-slate-900 font-sans">
                          {formatCurrency(item.total)}
                        </span>
                        <span className="text-[11px] font-mono font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 min-w-[48px] text-center">
                          {item.percentage.toFixed(1)}%
                        </span>
                      </div>
                    </div>

                    {/* Progress Fill Bar */}
                    <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden mt-2">
                      <div
                        className="h-full rounded-full transition-all duration-300"
                        style={{
                          width: `${item.percentage}%`,
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
