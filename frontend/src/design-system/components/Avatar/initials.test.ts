import { describe, expect, it } from 'vitest';
import { initials } from './initials';

describe('initials', () => {
  it('uses the first and last part of a name or username', () => {
    expect(initials('Nimal Perera')).toBe('NP');
    expect(initials('Nimal Kumara Perera')).toBe('NP');
    expect(initials('kasun.silva')).toBe('KS');
    expect(initials('umesh')).toBe('U');
    expect(initials('  ')).toBe('?');
  });
});
