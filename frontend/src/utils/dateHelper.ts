import type { Transaction } from '../types/transaction';

export interface DateRangeLimit {
  minDate: Date;
  maxDate: Date;
  minDateStr: string;
  maxDateStr: string;
}

export type DatePresetId = 'all_time' | 'this_month' | 'last_30_days' | 'last_7_days';

export interface DatePreset {
  id: DatePresetId;
  label: string;
  sublabel: string;
  startDate: Date;
  endDate: Date;
}

/**
 * Parses a date string (e.g. "2019-10-31 18:04:00", "2019-10-31", "2025-05-11T00:00:00Z")
 * into a normalized local Date object set to 00:00:00.
 */
export function parseDateOnly(dateStr: string): Date | null {
  if (!dateStr || typeof dateStr !== 'string') return null;

  const datePart = dateStr.trim().split(/[\sT]/)[0];
  const parts = datePart.split('-');

  if (parts.length === 3) {
    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    const day = parseInt(parts[2], 10);

    if (!isNaN(year) && !isNaN(month) && !isNaN(day)) {
      const d = new Date(year, month, day, 0, 0, 0, 0);
      if (!isNaN(d.getTime())) return d;
    }
  }

  const fallback = new Date(dateStr);
  if (!isNaN(fallback.getTime())) {
    return new Date(fallback.getFullYear(), fallback.getMonth(), fallback.getDate(), 0, 0, 0, 0);
  }

  return null;
}

/**
 * Formats a Date to a short friendly string (e.g. "Oct 31, 2019" or "May 11, 2025")
 */
export function formatFriendlyDate(d: Date, includeYear = true): string {
  if (!d || isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: includeYear ? 'numeric' : undefined,
  });
}

/**
 * Formats month and year (e.g. "October 2019", "May 2025")
 */
export function formatMonthYear(d: Date): string {
  if (!d || isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });
}

/**
 * Detects the minimum and maximum dates present across a list of transactions.
 */
export function calculateDateRangeLimits(transactions: Transaction[]): DateRangeLimit {
  if (!transactions || transactions.length === 0) {
    const today = new Date();
    return {
      minDate: new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000),
      maxDate: today,
      minDateStr: '',
      maxDateStr: '',
    };
  }

  let minTimestamp = Infinity;
  let maxTimestamp = -Infinity;

  transactions.forEach((tx) => {
    const d = parseDateOnly(tx.date);
    if (d) {
      const time = d.getTime();
      if (time < minTimestamp) minTimestamp = time;
      if (time > maxTimestamp) maxTimestamp = time;
    }
  });

  // Fallback de seguridad por si el arreglo viene corrupto
  if (minTimestamp === Infinity || maxTimestamp === -Infinity) {
    const today = new Date();
    return {
      minDate: new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000),
      maxDate: today,
      minDateStr: '',
      maxDateStr: '',
    };
  }

  const minDate = new Date(minTimestamp);
  const maxDate = new Date(maxTimestamp);

  const formatISO = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  console.log('--- RANGO CALCULADO DEL CSV ---');
  console.log('Min Date:', formatISO(minDate));
  console.log('Max Date:', formatISO(maxDate)); // <-- Esto debe decir 2019-10-31

  return {
    minDate,
    maxDate,
    minDateStr: formatISO(minDate),
    maxDateStr: formatISO(maxDate),
  };
}


/**
 * Builds dynamic presets based on the dataset's maxDate (as reference current day)
 * and minDate.
 */
export function generateDatePresets(limits: DateRangeLimit): DatePreset[] {
  const { minDate, maxDate } = limits;
  const sameYear = minDate.getFullYear() === maxDate.getFullYear();

  // 1. All Time: Exact span from oldest to newest transaction
  const allTimeLabel = 'All Time';
  const allTimeSublabel = `${formatFriendlyDate(minDate, !sameYear)} – ${formatFriendlyDate(maxDate, true)}`;

  // 2. This Month: Relative to maxDate's month
  const monthStart = new Date(maxDate.getFullYear(), maxDate.getMonth(), 1, 0, 0, 0, 0);
  const monthEnd = new Date(maxDate.getFullYear(), maxDate.getMonth() + 1, 0, 23, 59, 59, 999);
  const thisMonthLabel = `This Month (${formatMonthYear(maxDate)})`;
  const thisMonthSublabel = `${formatFriendlyDate(monthStart)} – ${formatFriendlyDate(monthEnd)}`;

  // 3. Last 30 Days: Relative to maxDate
  const last30Start = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate() - 29, 0, 0, 0, 0);
  const last30End = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate(), 23, 59, 59, 999);
  const last30Label = 'Last 30 Days';
  const last30Sublabel = `${formatFriendlyDate(last30Start)} – ${formatFriendlyDate(maxDate, true)}`;

  // 4. Last 7 Days: Relative to maxDate
  const last7Start = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate() - 6, 0, 0, 0, 0);
  const last7End = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate(), 23, 59, 59, 999);
  const last7Label = 'Last 7 Days';
  const last7Sublabel = `${formatFriendlyDate(last7Start)} – ${formatFriendlyDate(maxDate, true)}`;

  return [
    {
      id: 'all_time',
      label: allTimeLabel,
      sublabel: allTimeSublabel,
      startDate: minDate,
      endDate: maxDate,
    },
    {
      id: 'this_month',
      label: thisMonthLabel,
      sublabel: thisMonthSublabel,
      startDate: monthStart,
      endDate: monthEnd,
    },
    {
      id: 'last_30_days',
      label: last30Label,
      sublabel: last30Sublabel,
      startDate: last30Start,
      endDate: last30End,
    },
    {
      id: 'last_7_days',
      label: last7Label,
      sublabel: last7Sublabel,
      startDate: last7Start,
      endDate: last7End,
    },
  ];
}

/**
 * Checks if a transaction's date is within [startDate, endDate] inclusive.
 */
export function isDateWithinRange(txDateStr: string, startDate: Date, endDate: Date): boolean {
  const d = parseDateOnly(txDateStr);
  if (!d) return false;

  const start = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate(), 0, 0, 0, 0).getTime();
  const end = new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate(), 23, 59, 59, 999).getTime();
  const time = d.getTime();

  return time >= start && time <= end;
}
