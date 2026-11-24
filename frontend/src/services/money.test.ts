import { describe, expect, it } from 'vitest';
import { toNumber, toNumberOrNull } from './money';

describe('money', () => {
  it('turns the API strings into numbers', () => {
    expect(toNumber('147.06')).toBe(147.06);
    expect(toNumber('-20000.00')).toBe(-20000);
  });

  it('keeps null as null', () => {
    expect(toNumberOrNull(null)).toBeNull();
    expect(toNumberOrNull('0.00')).toBe(0);
  });
});
