import type { GoalStatus } from '../types/goal';

/** Label, colors and icon of each goal status (same palette as the budgets). */
export const GOAL_STATUS_STYLES: Record<GoalStatus, { label: string; bar: string; badge: string; icon: string }> = {
  OVERDUE: { label: 'Overdue', bar: 'bg-rose-500', badge: 'bg-rose-50 text-rose-700 border-rose-200', icon: 'event_busy' },
  BEHIND: { label: 'Behind', bar: 'bg-amber-500', badge: 'bg-amber-50 text-amber-700 border-amber-200', icon: 'warning' },
  ON_TRACK: { label: 'On track', bar: 'bg-emerald-500', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200', icon: 'check_circle' },
  NO_TARGET_DATE: { label: 'No deadline', bar: 'bg-sky-500', badge: 'bg-slate-100 text-slate-600 border-slate-200', icon: 'all_inclusive' },
  COMPLETED: { label: 'Completed', bar: 'bg-emerald-600', badge: 'bg-emerald-600 text-white border-emerald-600', icon: 'celebration' },
};

/** Goals that need the user's attention: they feed the sidebar badge. */
export const needsAttention = (status: GoalStatus) => status === 'BEHIND' || status === 'OVERDUE';

/** "Jun 2027". */
export const monthYear = (date: string) =>
  new Date(`${date}T00:00:00`).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
