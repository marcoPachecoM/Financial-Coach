import { describe, expect, it } from 'vitest';
import type { ApiAlert } from '../types/api';
import { toInsight } from './alertMessages';

const alert = (type: ApiAlert['type'], severity: ApiAlert['severity'], data: Record<string, unknown>,
               action: ApiAlert['action'] = null): ApiAlert => ({ id: `${type}:1`, type, severity, data, action });

describe('toInsight', () => {
  it('budget close to the limit', () => {
    const insight = toInsight(alert('BUDGET_WARNING', 'WARNING',
      { month: '2026-11', budget: '1000.00', spent: '850.00', remaining: '150.00', percentage_used: '85.00' }));

    expect(insight).toMatchObject({ id: 'BUDGET_WARNING:1', severity: 'warning', icon: 'warning', actions: [] });
    expect(insight.title).toContain('November');
    expect(insight.description).toBe('Current: $850.00 / $1,000.00 (85% utilized)');
  });

  it('budget exceeded shows how much over, even without a percentage (zero budget)', () => {
    const insight = toInsight(alert('BUDGET_EXCEEDED', 'CRITICAL',
      { month: '2026-11', budget: '0.00', spent: '20.00', remaining: '-20.00', percentage_used: null }));

    expect(insight.severity).toBe('critical');
    expect(insight.description).toBe('Spent $20.00 of $0.00 · $20.00 over budget.');
  });

  it('category budgets name the group', () => {
    const data = { month: '2026-11', category_group: 'Food', budget: '200.00', spent: '250.00',
      remaining: '-50.00', percentage_used: '125.00' };

    expect(toInsight(alert('CATEGORY_BUDGET_EXCEEDED', 'CRITICAL', data)).title)
      .toBe('Food is over its budget for November.');
    expect(toInsight(alert('CATEGORY_BUDGET_WARNING', 'WARNING', { ...data, spent: '170.00', percentage_used: '85.00' })).title)
      .toBe('Food is close to its budget for November.');
  });

  it('recurring alerts carry their action', () => {
    const missing = toInsight(alert('RECURRING_MISSING', 'WARNING',
      { name: 'Netflix', amount: '199.00', due_date: '2026-11-05' }, 'STOP_RECURRING'));
    const price = toInsight(alert('RECURRING_PRICE_CHANGED', 'WARNING',
      { name: 'Spotify', expected_amount: '129.00', charged_amount: '149.00', change_percentage: '15.50',
        due_date: '2026-11-10' }, 'UPDATE_RECURRING_AMOUNT'));
    const suggested = toInsight(alert('RECURRING_SUGGESTED', 'INFO',
      { name: 'Gym', amount: '450.00', frequency: 'MONTHLY', occurrences: 3 }, 'CREATE_RECURRING'));

    expect(missing.actions).toEqual([{ id: 'STOP_RECURRING', label: 'Stop recurring' }]);
    expect(price.icon).toBe('trending_up');
    expect(price.description).toContain('+15.5%');
    expect(suggested.actions[0].label).toBe('Mark as recurring');
    expect(suggested.description).toContain('monthly');
  });

  it('upcoming charges and the subscription audit', () => {
    expect(toInsight(alert('RECURRING_UPCOMING', 'INFO', { name: 'Gym', amount: '450.00', due_date: '2026-11-25' })).title)
      .toBe('Upcoming: Gym on Nov 25, 2026.');
    expect(toInsight(alert('SUBSCRIPTION_AUDIT', 'INFO', { month: '2026-11', count: 3, names: ['Netflix', 'Spotify', 'Gym'] }))
      .description).toBe('3 recurring charges (Netflix, Spotify & Gym) verified with zero price increases.');
  });
});
