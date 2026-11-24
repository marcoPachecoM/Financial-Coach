import React from 'react';
import type { Goal } from '../../types/goal';
import { formatCurrency } from '../../utils/formatters';
import { GOAL_STATUS_STYLES, monthYear } from '../../utils/goalStyles';

interface GoalCardProps {
  goal: Goal;
  onOpen: () => void;
  onAddMoney: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

/**
 * What the user needs from a goal, in order: how much is saved, whether it arrives on time, and
 * what to do about it. Status lines are sentences, not percentages.
 */
const statusLines = (goal: Goal): { main: string; detail: string | null; tone: string } => {
  const pace = goal.monthlyPace > 0 ? `You're saving ${formatCurrency(goal.monthlyPace)}/mo` : null;
  const needs =
    goal.targetDate && goal.requiredMonthly !== null
      ? `Target ${monthYear(goal.targetDate)} · needs ${formatCurrency(goal.requiredMonthly)}/mo`
      : null;
  switch (goal.status) {
    case 'COMPLETED':
      return { main: `Done 🎉 You saved ${formatCurrency(goal.saved)}.`, detail: null, tone: 'text-emerald-700' };
    case 'OVERDUE':
      return {
        main: `The target was ${monthYear(goal.targetDate!)} · ${formatCurrency(goal.remaining)} to go`,
        detail: 'Move the date or add money to finish it.',
        tone: 'text-rose-600',
      };
    case 'ON_TRACK':
      return {
        main: needs ?? '',
        detail: `${pace} → on track for ${monthYear(goal.projectedDate!)}`,
        tone: 'text-emerald-700',
      };
    case 'BEHIND': {
      if (!pace) return { main: needs ?? '', detail: 'Add money to see a projection.', tone: 'text-amber-700' };
      const gap = (goal.requiredMonthly ?? 0) - goal.monthlyPace;
      return {
        main: needs ?? '',
        detail: `${pace} → ⚠ ${monthYear(goal.projectedDate!)}. Add ${formatCurrency(gap)}/mo more to arrive on time.`,
        tone: 'text-amber-700',
      };
    }
    case 'NO_TARGET_DATE':
      return pace
        ? { main: `${pace} → done in ${monthYear(goal.projectedDate!)}`, detail: null, tone: 'text-slate-600' }
        : { main: 'No deadline.', detail: 'Add money to see a projection.', tone: 'text-slate-500' };
  }
};

export const GoalCard: React.FC<GoalCardProps> = ({ goal, onOpen, onAddMoney, onEdit, onDelete }) => {
  const style = GOAL_STATUS_STYLES[goal.status];
  const lines = statusLines(goal);
  const width = Math.min(100, goal.percentage);
  const stop = (action: () => void) => (e: React.MouseEvent) => {
    e.stopPropagation();
    action();
  };

  return (
    <article
      onClick={onOpen}
      className="group bg-white rounded-2xl p-5 border border-slate-200/80 card-shadow hover:border-emerald-300 transition-colors cursor-pointer flex flex-col gap-3"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <button
            onClick={stop(onOpen)}
            className="text-left text-base font-bold text-slate-900 hover:text-emerald-700 truncate max-w-full cursor-pointer"
          >
            {goal.name}
          </button>
          <span className={`mt-1 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border ${style.badge}`}>
            <span aria-hidden="true" className="material-symbols-outlined !text-[13px]">{style.icon}</span>
            {style.label}
          </span>
        </div>
        <div className="flex items-center gap-0.5 shrink-0">
          <button
            onClick={stop(onEdit)}
            aria-label={`Edit ${goal.name}`}
            title="Edit"
            className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[18px]">edit</span>
          </button>
          <button
            onClick={stop(onDelete)}
            aria-label={`Delete ${goal.name}`}
            title="Delete"
            className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[18px]">delete</span>
          </button>
        </div>
      </div>

      <div>
        <p className="text-sm text-slate-600">
          <strong className="text-lg text-slate-900">{formatCurrency(goal.saved)}</strong> of{' '}
          {formatCurrency(goal.targetAmount)}
        </p>
        <div
          className="mt-2 h-2.5 rounded-full bg-slate-100 overflow-hidden"
          role="progressbar"
          aria-label={`${goal.name} progress`}
          aria-valuenow={Math.round(width)}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <div className={`h-full rounded-full transition-all ${style.bar}`} style={{ width: `${width}%` }} />
        </div>
        <p className="mt-1 text-[11px] text-slate-400 text-right">{Math.round(goal.percentage)}%</p>
      </div>

      <div className="text-xs space-y-0.5">
        {lines.main && <p className="text-slate-600">{lines.main}</p>}
        {lines.detail && <p className={`font-medium ${lines.tone}`}>{lines.detail}</p>}
      </div>

      {goal.status !== 'COMPLETED' && (
        <button
          onClick={stop(onAddMoney)}
          className="mt-auto self-start inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-semibold cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[16px]">add</span>
          Add money
        </button>
      )}
    </article>
  );
};
