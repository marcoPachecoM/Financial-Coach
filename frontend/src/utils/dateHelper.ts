
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
 * Formats a Date object to YYYY-MM-DD string using local time (avoids UTC offset shifts)
 */
export function formatLocalDateToISO(d: Date): string {
  if (!d || isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Parses a date string safely into local midnight
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
      return new Date(year, month, day, 0, 0, 0, 0);
    }
  }

  const fallback = new Date(dateStr);
  if (!isNaN(fallback.getTime())) {
    return new Date(fallback.getFullYear(), fallback.getMonth(), fallback.getDate(), 0, 0, 0, 0);
  }

  return null;
}

export function formatFriendlyDate(d: Date, includeYear = true): string {
  if (!d || isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: includeYear ? 'numeric' : undefined,
  });
}

export function formatMonthYear(d: Date): string {
  if (!d || isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });
}

/**
 * Date boundaries for the presets: the account's first and last transaction ("YYYY-MM-DD", from
 * GET /analytics/summary). Without transactions, the last 30 days.
 */
export function calculateDateRangeLimits(firstDate: string | null, lastDate: string | null): DateRangeLimit {
  const min = firstDate ? parseDateOnly(firstDate) : null;
  const max = lastDate ? parseDateOnly(lastDate) : null;
  if (!min || !max) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000);
    return {
      minDate: thirtyDaysAgo,
      maxDate: today,
      minDateStr: formatLocalDateToISO(thirtyDaysAgo),
      maxDateStr: formatLocalDateToISO(today),
    };
  }
  return { minDate: min, maxDate: max, minDateStr: formatLocalDateToISO(min), maxDateStr: formatLocalDateToISO(max) };
}

export function generateDatePresets(limits: DateRangeLimit): DatePreset[] {
  const { minDate, maxDate } = limits;
  const sameYear = minDate.getFullYear() === maxDate.getFullYear();

  const allTimeLabel = 'All Time';
  const allTimeSublabel = `${formatFriendlyDate(minDate, !sameYear)} – ${formatFriendlyDate(maxDate, true)}`;

  const monthStart = new Date(maxDate.getFullYear(), maxDate.getMonth(), 1, 0, 0, 0, 0);
  const monthEnd = new Date(maxDate.getFullYear(), maxDate.getMonth() + 1, 0, 23, 59, 59, 999);
  const thisMonthLabel = `This Month (${formatMonthYear(maxDate)})`;
  const thisMonthSublabel = `${formatFriendlyDate(monthStart)} – ${formatFriendlyDate(monthEnd)}`;

  const last30Start = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate() - 29, 0, 0, 0, 0);
  const last30End = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate(), 23, 59, 59, 999);
  const last30Label = 'Last 30 Days';
  const last30Sublabel = `${formatFriendlyDate(last30Start)} – ${formatFriendlyDate(maxDate, true)}`;

  const last7Start = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate() - 6, 0, 0, 0, 0);
  const last7End = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate(), 23, 59, 59, 999);
  const last7Label = 'Last 7 Days';
  const last7Sublabel = `${formatFriendlyDate(last7Start)} – ${formatFriendlyDate(maxDate, true)}`;

  return [
    { id: 'all_time', label: allTimeLabel, sublabel: allTimeSublabel, startDate: minDate, endDate: maxDate },
    { id: 'this_month', label: thisMonthLabel, sublabel: thisMonthSublabel, startDate: monthStart, endDate: monthEnd },
    { id: 'last_30_days', label: last30Label, sublabel: last30Sublabel, startDate: last30Start, endDate: last30End },
    { id: 'last_7_days', label: last7Label, sublabel: last7Sublabel, startDate: last7Start, endDate: last7End },
  ];
}
