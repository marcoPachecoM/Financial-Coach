import React, { useMemo } from 'react';
import type { Goal, GoalContribution } from '../../types/goal';
import { formatCurrency } from '../../utils/formatters';
import { monthYear } from '../../utils/goalStyles';
import { formatLocalDateToISO } from '../../utils/dateHelper';

interface GoalChartProps {
  goal: Goal;
  /** Any order. */
  contributions: GoalContribution[];
  /** "YYYY-MM-DD". */
  today: string;
}

const WIDTH = 600;
const HEIGHT = 220;
const PAD = { top: 24, right: 16, bottom: 28, left: 16 };
const DAY_MS = 24 * 60 * 60 * 1000;
/** A projection years away would squash the history: the axis stops here and the line is cut. */
const MAX_FUTURE_DAYS = 3 * 365;

const time = (date: string) => new Date(`${date}T00:00:00`).getTime();

/**
 * Saved so far (solid line, one step per contribution day), where the current pace leads
 * (dashed), the target (horizontal) and the deadline (vertical). Inline SVG, like the other charts.
 */
export const GoalChart: React.FC<GoalChartProps> = ({ goal, contributions, today }) => {
  const chart = useMemo(() => {
    // Cumulative total at the end of each day with contributions, oldest first.
    const byDay = new Map<string, number>();
    [...contributions]
      .sort((a, b) => a.date.localeCompare(b.date))
      .forEach((c) => byDay.set(c.date, (byDay.get(c.date) ?? 0) + c.amount));
    let running = 0;
    const history = [...byDay.entries()].map(([date, amount]) => ({ t: time(date), value: (running += amount) }));

    const now = time(today);
    const start = Math.min(history[0]?.t ?? now, now - 30 * DAY_MS);
    const limit = now + MAX_FUTURE_DAYS * DAY_MS;
    const candidates = [now + 30 * DAY_MS];
    if (goal.targetDate) candidates.push(time(goal.targetDate));
    if (goal.projectedDate) candidates.push(time(goal.projectedDate));
    const end = Math.min(limit, Math.max(...candidates));
    const top = Math.max(goal.targetAmount, goal.saved, ...history.map((p) => p.value)) * 1.08;

    const x = (t: number) => PAD.left + ((t - start) / (end - start)) * (WIDTH - PAD.left - PAD.right);
    const y = (v: number) => PAD.top + (1 - v / top) * (HEIGHT - PAD.top - PAD.bottom);

    // Steps: flat until the next contribution, then up (or down for a withdrawal). Ends today.
    const points: string[] = [`${x(start)},${y(0)}`];
    let last = 0;
    history.forEach((p) => {
      points.push(`${x(p.t)},${y(last)}`, `${x(p.t)},${y(p.value)}`);
      last = p.value;
    });
    points.push(`${x(now)},${y(last)}`);

    let projection: { x1: number; y1: number; x2: number; y2: number } | null = null;
    if (goal.projectedDate && goal.status !== 'COMPLETED') {
      const target = time(goal.projectedDate);
      // Cut at the axis end, at the value reached by then.
      const cut = Math.min(target, end);
      const reached = goal.saved + ((cut - now) / (target - now || 1)) * (goal.targetAmount - goal.saved);
      projection = { x1: x(now), y1: y(goal.saved), x2: x(cut), y2: y(reached) };
    }

    return {
      line: points.join(' '),
      projection,
      targetY: y(goal.targetAmount),
      deadlineX: goal.targetDate ? x(Math.min(time(goal.targetDate), end)) : null,
      todayX: x(now),
      startLabel: monthYear(formatLocalDateToISO(new Date(start))),
      endLabel: monthYear(formatLocalDateToISO(new Date(end))),
      baseY: y(0),
    };
  }, [goal, contributions, today]);

  const deadlineColor = goal.status === 'OVERDUE' ? '#e11d48' : '#d97706';

  return (
    <figure>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="w-full h-auto"
        role="img"
        aria-label={
          `${goal.name}: ${formatCurrency(goal.saved)} saved of ${formatCurrency(goal.targetAmount)}` +
          (goal.projectedDate ? `, projected for ${monthYear(goal.projectedDate)}` : '') +
          (goal.targetDate ? `, target ${monthYear(goal.targetDate)}` : '')
        }
      >
        <line x1={PAD.left} x2={WIDTH - PAD.right} y1={chart.baseY} y2={chart.baseY} stroke="#e2e8f0" />
        {/* Target */}
        <line
          x1={PAD.left}
          x2={WIDTH - PAD.right}
          y1={chart.targetY}
          y2={chart.targetY}
          stroke="#94a3b8"
          strokeDasharray="2 4"
        />
        <text x={PAD.left} y={chart.targetY - 6} fontSize="11" fill="#64748b">
          Target {formatCurrency(goal.targetAmount)}
        </text>
        {/* Deadline */}
        {chart.deadlineX !== null && (
          <>
            <line
              x1={chart.deadlineX}
              x2={chart.deadlineX}
              y1={PAD.top - 8}
              y2={chart.baseY}
              stroke={deadlineColor}
              strokeDasharray="4 3"
            />
            <text x={chart.deadlineX - 4} y={PAD.top - 12} fontSize="11" fill={deadlineColor} textAnchor="end">
              {monthYear(goal.targetDate!)}
            </text>
          </>
        )}
        {/* Today */}
        <line x1={chart.todayX} x2={chart.todayX} y1={PAD.top} y2={chart.baseY} stroke="#cbd5e1" />
        {/* Projection at the current pace */}
        {chart.projection && (
          <line
            {...chart.projection}
            stroke="#10b981"
            strokeWidth="2"
            strokeDasharray="6 5"
            strokeLinecap="round"
          />
        )}
        {/* Saved so far */}
        <polyline points={chart.line} fill="none" stroke="#059669" strokeWidth="2.5" strokeLinejoin="round" />
        <text x={PAD.left} y={HEIGHT - 8} fontSize="11" fill="#94a3b8">
          {chart.startLabel}
        </text>
        <text x={chart.todayX} y={HEIGHT - 8} fontSize="11" fill="#64748b" textAnchor="middle">
          Today
        </text>
        <text x={WIDTH - PAD.right} y={HEIGHT - 8} fontSize="11" fill="#94a3b8" textAnchor="end">
          {chart.endLabel}
        </text>
      </svg>
      <figcaption className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
        <span className="inline-flex items-center gap-1.5">
          <span className="w-4 h-0.5 bg-emerald-600 rounded" /> Saved
        </span>
        {chart.projection && (
          <span className="inline-flex items-center gap-1.5">
            <span className="w-4 border-t-2 border-dashed border-emerald-500" /> At your pace
          </span>
        )}
        {chart.deadlineX !== null && (
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 border-l-2 border-dashed" style={{ borderColor: deadlineColor }} /> Target date
          </span>
        )}
      </figcaption>
    </figure>
  );
};
