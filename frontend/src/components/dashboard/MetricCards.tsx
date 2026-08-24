import React from 'react';
import type { MetricSummary } from '../../types/metrics';
import { formatCurrency } from '../../utils/formatters';
import { getCategoryStyle } from '../../utils/categoryHelper';

interface MetricCardsProps {
  metrics: MetricSummary;
}

export const MetricCards: React.FC<MetricCardsProps> = ({ metrics }) => {
  const isPositiveBalanceChange = metrics.balanceChangePercentage >= 0;
  const categoryStyle = getCategoryStyle(metrics.topCategoryName);

  return (
    <section className="grid grid-cols-1 md:grid-cols-3 gap-4">
      {/* 1. Total Balance */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow card-shadow-hover relative overflow-hidden group">
        <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-50 rounded-bl-full -mr-6 -mt-6 transition-transform group-hover:scale-110 pointer-events-none" />
        <div className="relative z-10">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
              Total Balance
            </p>
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[20px]">
                account_balance_wallet
              </span>
            </div>
          </div>
          <h3 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-sans mb-3">
            {formatCurrency(metrics.totalBalance)}
          </h3>
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-medium ${
                isPositiveBalanceChange
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : 'bg-rose-50 text-rose-700 border border-rose-200'
              }`}
            >
              <span className="material-symbols-outlined !text-[14px]">
                {isPositiveBalanceChange ? 'trending_up' : 'trending_down'}
              </span>
              {isPositiveBalanceChange ? '+' : ''}
              {metrics.balanceChangePercentage.toFixed(1)}%
            </span>
            <span className="text-xs text-slate-500 font-medium">vs last month</span>
          </div>
        </div>
      </div>

      {/* 2. Avg. Daily Spend */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow card-shadow-hover relative overflow-hidden group">
        <div className="absolute top-0 right-0 w-24 h-24 bg-blue-50 rounded-bl-full -mr-6 -mt-6 transition-transform group-hover:scale-110 pointer-events-none" />
        <div className="relative z-10">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
              Avg. Daily Spend
            </p>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[20px]">
                payments
              </span>
            </div>
          </div>
          <div className="flex items-baseline gap-1 mb-3">
            <h3 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-sans">
              {formatCurrency(metrics.avgDailySpend)}
            </h3>
            <span className="text-xs font-semibold text-slate-400 font-mono">/ day</span>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200 font-mono">
              <span className="material-symbols-outlined !text-[14px]">receipt_long</span>
              {metrics.transactionsPerDay.toFixed(1)} tx/day
            </span>
            <span className="text-xs text-slate-500 font-medium">
              {metrics.totalTransactionsCount} {metrics.totalTransactionsCount === 1 ? 'record' : 'records'} ({metrics.daysInRange}d)
            </span>
          </div>
        </div>
      </div>

      {/* 3. Top Category */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow card-shadow-hover relative overflow-hidden group">
        <div className="absolute top-0 right-0 w-24 h-24 bg-amber-50 rounded-bl-full -mr-6 -mt-6 transition-transform group-hover:scale-110 pointer-events-none" />
        <div className="relative z-10">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
              Top Category
            </p>
            <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[20px]">
                {categoryStyle.icon || 'pie_chart'}
              </span>
            </div>
          </div>
          <h3
            className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-sans mb-3 truncate"
            title={metrics.topCategoryName}
          >
            {metrics.topCategoryName}
          </h3>
          <div className="flex items-center gap-2 flex-wrap">
            {metrics.topCategoryAmount > 0 ? (
              <>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-800 border border-amber-200 font-mono">
                  {metrics.topCategoryPercentage.toFixed(1)}% of total
                </span>
                <span className="text-xs text-slate-500 font-medium">
                  {formatCurrency(metrics.topCategoryAmount)} spent
                </span>
              </>
            ) : (
              <span className="text-xs text-slate-400 font-medium">
                No spending in period
              </span>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

