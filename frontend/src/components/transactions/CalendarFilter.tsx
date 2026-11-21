import React, { useMemo } from 'react';
import type { Transaction } from '../../types/transaction';
import { formatCurrency } from '../../utils/formatters';

interface CalendarFilterProps {
  /** First day of the month shown. */
  month: Date;
  /** "YYYY-MM-DD" or null. */
  selectedDay: string | null;
  transactions: Transaction[];
  onChangeMonth: (month: Date) => void;
  onSelectDay: (day: string | null) => void;
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const pad = (n: number) => String(n).padStart(2, '0');
const dayKey = (year: number, monthIndex: number, day: number) => `${year}-${pad(monthIndex + 1)}-${pad(day)}`;

/** Background by share of the busiest day: written out in full so Tailwind generates the classes. */
const intensityClass = (ratio: number) =>
  ratio === 0
    ? 'bg-white'
    : ratio < 0.25
      ? 'bg-emerald-50'
      : ratio < 0.5
        ? 'bg-emerald-100'
        : ratio < 0.75
          ? 'bg-emerald-200'
          : 'bg-emerald-300';

/** Month grid; each day is shaded by how much was spent. Clicking a day filters the list below. */
export const CalendarFilter: React.FC<CalendarFilterProps> = ({
  month,
  selectedDay,
  transactions,
  onChangeMonth,
  onSelectDay,
}) => {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  // Monday-first grid: getDay() is 0 for Sunday.
  const leadingBlanks = (new Date(year, monthIndex, 1).getDay() + 6) % 7;
  const today = new Date();
  const todayKey = dayKey(today.getFullYear(), today.getMonth(), today.getDate());

  const monthPrefix = `${year}-${pad(monthIndex + 1)}`;
  const byDay = useMemo(() => {
    const totals = new Map<string, { spent: number; count: number }>();
    for (const tx of transactions) {
      if (!tx.datetime.startsWith(monthPrefix)) continue;
      const key = tx.datetime.slice(0, 10);
      const previous = totals.get(key) ?? { spent: 0, count: 0 };
      totals.set(key, {
        count: previous.count + 1,
        spent: previous.spent + (tx.type === 'EXPENSE' ? Math.abs(tx.amount) : 0),
      });
    }
    return totals;
  }, [transactions, monthPrefix]);

  const maxSpent = Math.max(0, ...[...byDay.values()].map((d) => d.spent));
  const monthSpent = [...byDay.values()].reduce((sum, d) => sum + d.spent, 0);

  const shift = (delta: number) => {
    onSelectDay(null);
    onChangeMonth(new Date(year, monthIndex + delta, 1));
  };

  return (
    <section className="bg-white rounded-2xl p-5 border border-slate-200/80 card-shadow">
      <div className="flex items-center justify-between mb-3">
        <button
          onClick={() => shift(-1)}
          className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 cursor-pointer"
          title="Previous month"
        >
          <span className="material-symbols-outlined !text-[20px]">chevron_left</span>
        </button>
        <div className="text-center">
          <p className="text-sm font-bold text-slate-900">
            {month.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
          </p>
          <p className="text-[11px] text-slate-500 font-mono">{formatCurrency(monthSpent)} spent</p>
        </div>
        <button
          onClick={() => shift(1)}
          className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 cursor-pointer"
          title="Next month"
        >
          <span className="material-symbols-outlined !text-[20px]">chevron_right</span>
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center">
        {WEEKDAYS.map((d) => (
          <div key={d} className="text-[10px] font-mono font-semibold uppercase text-slate-400 py-1">
            {d}
          </div>
        ))}
        {Array.from({ length: leadingBlanks }, (_, i) => (
          <div key={`blank-${i}`} />
        ))}
        {Array.from({ length: daysInMonth }, (_, i) => {
          const day = i + 1;
          const key = dayKey(year, monthIndex, day);
          const info = byDay.get(key);
          const selected = selectedDay === key;
          return (
            <button
              key={key}
              onClick={() => onSelectDay(selected ? null : key)}
              title={info ? `${info.count} transaction(s) · ${formatCurrency(info.spent)} spent` : 'No transactions'}
              className={`relative aspect-square sm:aspect-auto sm:h-12 rounded-lg border text-xs transition-all cursor-pointer flex flex-col items-center justify-center ${
                selected
                  ? 'border-emerald-600 ring-2 ring-emerald-200 bg-emerald-600 text-white font-bold'
                  : `${intensityClass(maxSpent ? (info?.spent ?? 0) / maxSpent : 0)} border-slate-100 hover:border-emerald-300 text-slate-700`
              } ${key === todayKey && !selected ? 'font-bold text-emerald-700' : ''}`}
            >
              {day}
              {info && (
                <span
                  className={`hidden sm:block text-[9px] font-mono leading-none mt-0.5 ${selected ? 'text-emerald-50' : 'text-slate-500'}`}
                >
                  {info.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="flex items-center justify-between mt-3 text-[11px] text-slate-500">
        <div className="flex items-center gap-1">
          <span>Less</span>
          {['bg-emerald-50', 'bg-emerald-100', 'bg-emerald-200', 'bg-emerald-300'].map((c) => (
            <span key={c} className={`w-3 h-3 rounded ${c} border border-slate-100`} />
          ))}
          <span>More spent</span>
        </div>
        {selectedDay && (
          <button onClick={() => onSelectDay(null)} className="text-emerald-600 font-semibold hover:underline cursor-pointer">
            Clear day
          </button>
        )}
      </div>
    </section>
  );
};
