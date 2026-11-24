import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { calculateDateRangeLimits, formatLocalDateToISO, generateDatePresets } from './dateHelper';

describe('formatLocalDateToISO', () => {
  it('uses the local calendar day', () => {
    expect(formatLocalDateToISO(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});

describe('calculateDateRangeLimits', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 10, 21, 10, 0));
  });
  afterEach(() => vi.useRealTimers());

  it('spans the first and last transaction', () => {
    const limits = calculateDateRangeLimits('2026-01-02', '2026-11-15');
    expect(limits.minDateStr).toBe('2026-01-02');
    expect(limits.maxDateStr).toBe('2026-11-15');
  });

  it('without transactions, covers the last 30 days up to today', () => {
    const limits = calculateDateRangeLimits(null, null);
    expect(limits.maxDateStr).toBe('2026-11-21');
    expect(limits.minDateStr).toBe('2026-10-22');
  });
});

describe('generateDatePresets', () => {
  it('anchors the presets to the last transaction, not to today', () => {
    const presets = generateDatePresets(calculateDateRangeLimits('2026-01-02', '2026-09-15'));
    const byId = Object.fromEntries(presets.map((p) => [p.id, p]));

    expect(presets.map((p) => p.id)).toEqual(['all_time', 'this_month', 'last_30_days', 'last_7_days']);
    expect(formatLocalDateToISO(byId.all_time.startDate)).toBe('2026-01-02');
    expect(formatLocalDateToISO(byId.this_month.startDate)).toBe('2026-09-01');
    expect(formatLocalDateToISO(byId.this_month.endDate)).toBe('2026-09-30');
    expect(formatLocalDateToISO(byId.last_30_days.startDate)).toBe('2026-08-17');
    expect(formatLocalDateToISO(byId.last_7_days.startDate)).toBe('2026-09-09');
    expect(byId.this_month.label).toBe('This Month (September 2026)');
  });
});
