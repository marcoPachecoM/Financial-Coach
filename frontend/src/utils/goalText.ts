import type { Goal } from '../types/goal';
import { formatCurrency } from './formatters';
import { monthYear } from './goalStyles';

/**
 * What the user needs from a goal, in order: how much is saved, whether it arrives on time, and
 * what to do about it. Status lines are sentences, not percentages.
 */
export const goalStatusLines = (goal: Goal): { main: string; detail: string | null; tone: string } => {
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
