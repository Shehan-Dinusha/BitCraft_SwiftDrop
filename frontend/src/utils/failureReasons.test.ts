import { describe, expect, it } from 'vitest';
import { failureLabel, failureReasons, failureTarget, failureTitle } from './failureReasons';

describe('failure reasons', () => {
  it('lets a courier at the door report anything, and only a loss on the way to a hub', () => {
    expect(failureReasons('OUT_FOR_DELIVERY', true)).toContain('RECIPIENT_REJECTED');
    expect(failureReasons('PICKED_UP', false)).toContain('RECIPIENT_UNAVAILABLE');
    expect(failureReasons('PICKED_UP', true)).toEqual(['DAMAGED_IN_TRANSIT', 'LOST']);
    expect(failureReasons('OUT_FOR_RETURN', false)).toEqual(['DAMAGED_IN_TRANSIT', 'LOST']);
    expect(failureReasons('ASSIGNED', false)).not.toContain('LOST');
    expect(failureTitle('ATTEMPT_FAILED', true)).toBe('Lost or damaged');
  });

  it('never lets the recipient pay', () => {
    expect(failureReasons('OUT_FOR_DELIVERY', false)).not.toContain('PAYMENT_FAILED');
  });

  it('is final only for a failed pickup, a loss or damage', () => {
    expect(failureTarget('OUT_FOR_DELIVERY', 'RECIPIENT_UNAVAILABLE')).toBe('ATTEMPT_FAILED');
    expect(failureTarget('OUT_FOR_DELIVERY', 'LOST')).toBe('FAILED');
    expect(failureTarget('ASSIGNED', 'RECIPIENT_UNAVAILABLE')).toBe('FAILED');
  });

  it('names old reasons too', () => {
    expect(failureLabel('REFUSED_BY_CUSTOMER')).toBe('Recipient refused it');
    expect(failureLabel(undefined)).toBe('No reason given');
  });
});
