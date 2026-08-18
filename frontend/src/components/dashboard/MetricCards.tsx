import React from 'react';
import type { MetricSummary } from '../../types/metrics';
import { formatCurrency } from '../../utils/formatters';

interface MetricCardsProps {
  metrics: MetricSummary;
}

export const MetricCards: React.FC<MetricCardsProps> = ({ metrics }) => {
  const isPositiveBalanceChange = metrics.balanceChangePercentage >= 0;
  const progressPercentage = Math.min(
    100,
    Math.max(0, (metrics.monthlySavingsRate / metrics.savingsTargetRate) * 100)
  );

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
            <span className="material-symbols-outlined text-slate-400 !text-[20px]">
              account_balance_wallet
            </span>
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

      {/* 2. Monthly Savings Rate */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow card-shadow-hover relative overflow-hidden group">
        <div className="absolute top-0 right-0 w-24 h-24 bg-teal-50 rounded-bl-full -mr-6 -mt-6 transition-transform group-hover:scale-110 pointer-events-none" />
        <div className="relative z-10">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
              Monthly Savings Rate
            </p>
            <span className="material-symbols-outlined text-slate-400 !text-[20px]">
              savings
            </span>
          </div>
          <div className="flex items-baseline justify-between mb-3">
            <h3 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-sans">
              {metrics.monthlySavingsRate.toFixed(1)}%
            </h3>
            <span className="text-xs font-semibold text-slate-500 font-mono">
              Target: {metrics.savingsTargetRate.toFixed(1)}%
            </span>
          </div>
          {/* Progress Bar */}
          <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden p-0.5 border border-slate-200/60">
            <div
              className="bg-gradient-to-r from-emerald-500 to-teal-400 h-full rounded-full transition-all duration-500 shadow-xs"
              style={{ width: `${progressPercentage}%` }}
            />
          </div>
          <div className="flex justify-between items-center mt-2 text-[11px] text-slate-400">
            <span>On track</span>
            <span>{(metrics.savingsTargetRate - metrics.monthlySavingsRate).toFixed(1)}% to goal</span>
          </div>
        </div>
      </div>

      {/* 3. Active AI Tasks */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 card-shadow card-shadow-hover relative overflow-hidden group">
        <div className="absolute top-0 right-0 w-24 h-24 bg-purple-50 rounded-bl-full -mr-6 -mt-6 transition-transform group-hover:scale-110 pointer-events-none" />
        <div className="relative z-10">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 font-mono">
              Active AI Tasks
            </p>
            <div className="w-7 h-7 rounded-lg bg-purple-100 text-purple-700 flex items-center justify-center">
              <span className="material-symbols-outlined !text-[18px]">smart_toy</span>
            </div>
          </div>
          <h3 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-sans mb-2">
            {metrics.activeAiTasksCount}
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed line-clamp-2">
            {metrics.activeAiTasksDescription}
          </p>
        </div>
      </div>
    </section>
  );
};
