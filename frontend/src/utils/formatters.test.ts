import { describe, expect, it } from 'vitest';
import { formatCurrency, formatDate, formatFullDate } from './formatters';

describe('formatCurrency', () => {
  it('always shows two decimals and thousands separators', () => {
    expect(formatCurrency(1234.5)).toBe('$1,234.50');
    expect(formatCurrency(0)).toBe('$0.00');
  });

  it('puts the minus sign before the symbol', () => {
    expect(formatCurrency(-45.5)).toBe('-$45.50');
  });

  it('rounds to cents', () => {
    expect(formatCurrency(10.005)).toMatch(/^\$10\.0[01]$/);
  });
});

describe('formatDate / formatFullDate', () => {
  it('reads the calendar day without shifting it by the time zone', () => {
    // "2026-11-01" must stay November 1st in every time zone (no UTC parsing).
    expect(formatDate('2026-11-01')).toBe('Nov 1');
    expect(formatFullDate('2026-11-01T23:59:00')).toBe('Nov 1, 2026');
  });

  it('returns invalid input as it came and empty for empty', () => {
    expect(formatDate('not a date')).toBe('not a date');
    expect(formatFullDate('')).toBe('');
  });
});
