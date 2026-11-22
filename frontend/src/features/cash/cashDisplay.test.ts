import { describe, expect, it } from 'vitest';
import type { CashHandoverResponse } from '@/services/api/cashApi';
import { handoverState, lkr, parseAmount } from './cashDisplay';

const handover = (over: Partial<CashHandoverResponse>) =>
  ({ status: 'COUNTED', expectedAmount: 770, ...over }) as CashHandoverResponse;

describe('cashDisplay', () => {
  it('shows the difference of a short or over handover, and the amount of a matching one', () => {
    expect(handoverState(handover({ status: 'SUBMITTED' }))).toEqual({
      state: 'pending',
      amount: 770
    });
    expect(handoverState(handover({ difference: -70 }))).toEqual({ state: 'shortage', amount: 70 });
    expect(handoverState(handover({ difference: 30 }))).toEqual({ state: 'excess', amount: 30 });
    expect(handoverState(handover({ difference: 0 }))).toEqual({
      state: 'handed-over',
      amount: 770
    });
  });

  it('reads amounts with separators and refuses anything else', () => {
    expect(parseAmount('1,340.50')).toBe(1340.5);
    expect(parseAmount('0')).toBe(0);
    for (const bad of ['', 'abc', '-5', '1.234', '1e3']) expect(parseAmount(bad)).toBeNull();
  });

  it('formats amounts with two decimals', () => {
    expect(lkr(1340)).toBe('LKR 1,340.00');
  });
});
