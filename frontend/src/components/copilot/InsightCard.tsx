import React from 'react';
import type { AlertAction } from '../../types/api';
import type { Insight, InsightSeverity } from '../../types/copilot';

interface InsightCardProps {
  insight: Insight;
  /** True while one of its actions is running. */
  busy?: boolean;
  onAction: (insightId: string, action: AlertAction) => void;
  onDismiss: (insightId: string) => void;
}

const STYLES: Record<InsightSeverity, { card: string; icon: string }> = {
  critical: { card: 'bg-rose-50/40 border-rose-200/80', icon: 'bg-rose-100 text-rose-700' },
  warning: { card: 'bg-amber-50/40 border-amber-200/80', icon: 'bg-amber-100 text-amber-700' },
  info: {
    card: 'bg-white border-slate-200/90 card-shadow',
    icon: 'bg-gradient-to-tr from-emerald-500 to-teal-400 text-white',
  },
};

export const InsightCard: React.FC<InsightCardProps> = ({ insight, busy = false, onAction, onDismiss }) => {
  const style = STYLES[insight.severity];

  return (
    <div className={`rounded-2xl p-4 border transition-all duration-200 ${style.card}`}>
      <div className="flex items-start gap-3">
        <div className={`w-7 h-7 rounded-xl flex items-center justify-center shrink-0 mt-0.5 ${style.icon}`}>
          <span className="material-symbols-outlined !text-[16px]">{insight.icon}</span>
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <p className="text-xs font-bold text-slate-900 leading-snug">{insight.title}</p>
            <button
              onClick={() => onDismiss(insight.id)}
              title="Dismiss"
              className="text-slate-300 hover:text-slate-500 shrink-0 -mt-0.5 cursor-pointer"
            >
              <span className="material-symbols-outlined !text-[16px]">close</span>
            </button>
          </div>
          <p className="text-xs text-slate-500 mt-1 leading-relaxed">{insight.description}</p>

          {insight.actions.length > 0 && (
            <div className="flex items-center gap-2 mt-3">
              {insight.actions.map((action) => (
                <button
                  key={action.id}
                  disabled={busy}
                  onClick={() => onAction(insight.id, action.id)}
                  className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 disabled:opacity-50 disabled:cursor-wait text-white px-3 py-1.5 rounded-xl text-xs font-semibold transition-all flex items-center gap-1 shadow-xs cursor-pointer"
                >
                  <span>{busy ? 'Working...' : action.label}</span>
                  <span className="material-symbols-outlined !text-[14px]">bolt</span>
                </button>
              ))}
              <button
                onClick={() => onDismiss(insight.id)}
                className="bg-slate-100 hover:bg-slate-200 text-slate-600 px-3 py-1.5 rounded-xl text-xs font-medium transition-colors cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
