import { describe, expect, it } from 'vitest';
import { TAG_MAX_LENGTH, cleanTagName } from './tagNames';

describe('cleanTagName', () => {
  it('trims, collapses spaces and drops a leading #', () => {
    expect(cleanTagName('  #Trip   Cancun ')).toBe('Trip Cancun');
    expect(cleanTagName('# work')).toBe('work');
  });

  it('keeps a # that is not at the start, and empty stays empty', () => {
    expect(cleanTagName('c#')).toBe('c#');
    expect(cleanTagName('   ')).toBe('');
  });

  it('uses the API limit', () => {
    expect(TAG_MAX_LENGTH).toBe(40);
  });
});
