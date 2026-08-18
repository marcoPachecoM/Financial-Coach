import React from 'react';
import type { Insight } from '../../types/copilot';

interface InsightCardProps {
  insight: Insight;
  onExecute: (id: string) => void;
  onDismiss: (id: string) => void;
}

export const InsightCard: React.FC<InsightCardProps> = ({
  insight,
  onExecute,
  onDismiss,
}) => {
  if (insight.status === 'dismissed') return null;

  const isExecuted = insight.status === 'executed';

  return (
    <div
      className={`rounded-2xl p-4 border transition-all duration-200 ${
        isExecuted
          ? 'bg-emerald-50/50 border-emerald-200'
          : insight.type === 'alert'
          ? 'bg-amber-50/40 border-amber-200/80'
          : 'bg-white border-slate-200/90 card-shadow'
      }`}
    >
      <div className="flex items-start gap-3">
        {/* Icon / Avatar */}
        <div
          className={`w-7 h-7 rounded-xl flex items-center justify-center shrink-0 mt-0.5 ${
            insight.type === 'alert'
              ? 'bg-amber-100 text-amber-700'
              : isExecuted
              ? 'bg-emerald-100 text-emerald-700'
              : 'bg-gradient-to-tr from-emerald-500 to-teal-400 text-white'
          }`}
        >
          <span className="material-symbols-outlined !text-[16px]">
            {isExecuted
              ? 'check_circle'
              : insight.type === 'alert'
              ? 'warning'
              : 'smart_toy'}
          </span>
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <p className="text-xs font-bold text-slate-900 leading-snug">
            {insight.title}
          </p>
          <p className="text-xs text-slate-500 mt-1 leading-relaxed">
            {insight.description}
          </p>

          {/* Action Buttons for Optimization */}
          {insight.type === 'optimization' && !isExecuted && (
            <div className="flex items-center gap-2 mt-3">
              <button
                onClick={() => onExecute(insight.id)}
                className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white px-3 py-1.5 rounded-xl text-xs font-semibold transition-all flex items-center gap-1 shadow-xs cursor-pointer"
              >
                <span>{insight.actionText || 'Execute'}</span>
                <span className="material-symbols-outlined !text-[14px]">bolt</span>
              </button>
              <button
                onClick={() => onDismiss(insight.id)}
                className="bg-slate-100 hover:bg-slate-200 text-slate-600 px-3 py-1.5 rounded-xl text-xs font-medium transition-colors cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          )}

          {isExecuted && (
            <div className="mt-2 flex items-center gap-1 text-[11px] font-semibold text-emerald-700 font-mono">
              <span className="material-symbols-outlined !text-[14px]">done_all</span>
              Executed Autonomously
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
