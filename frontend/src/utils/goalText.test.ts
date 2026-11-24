import { describe, expect, it } from 'vitest';
import type { Goal } from '../types/goal';
import { goalStatusLines } from './goalText';

const goal = (overrides: Partial<Goal>): Goal => ({
  id: 'g1', name: 'Trip', targetAmount: 40000, targetDate: '2027-06-30', saved: 18000, remaining: 22000,
  percentage: 45, monthlyPace: 2100, requiredMonthly: 2750, projectedDate: '2027-08-14', status: 'BEHIND',
  ...overrides,
});

describe('goalStatusLines', () => {
  it('behind: what it needs, the pace, and how much more per month', () => {
    const lines = goalStatusLines(goal({}));
    expect(lines.main).toBe('Target Jun 2027 · needs $2,750.00/mo');
    expect(lines.detail).toBe("You're saving $2,100.00/mo → ⚠ Aug 2027. Add $650.00/mo more to arrive on time.");
  });

  it('behind without money yet asks for a first contribution', () => {
    expect(goalStatusLines(goal({ monthlyPace: 0, projectedDate: null })).detail).toBe('Add money to see a projection.');
  });

  it('on track names the projected month', () => {
    expect(goalStatusLines(goal({ status: 'ON_TRACK', monthlyPace: 3000, projectedDate: '2027-04-01' })).detail)
      .toBe("You're saving $3,000.00/mo → on track for Apr 2027");
  });

  it('overdue, no deadline and completed', () => {
    expect(goalStatusLines(goal({ status: 'OVERDUE', targetDate: '2026-11-01', requiredMonthly: null })).main)
      .toBe('The target was Nov 2026 · $22,000.00 to go');
    expect(goalStatusLines(goal({ status: 'NO_TARGET_DATE', targetDate: null, requiredMonthly: null })).main)
      .toBe("You're saving $2,100.00/mo → done in Aug 2027");
    expect(goalStatusLines(goal({ status: 'COMPLETED', saved: 40000, remaining: 0 })).main)
      .toBe('Done 🎉 You saved $40,000.00.');
  });
});
