import { describe, expect, it } from 'vitest';
import { phoneDigits, phoneProblem, toE164 } from './phone';

const accepted = (input: string) => phoneProblem(phoneDigits(input)) === undefined;

describe('phone numbers', () => {
  it('accepts +94 and 9 digits, 0 and 9 digits, or just 9 digits, as the same number', () => {
    for (const input of ['+94771234567', '0771234567', '771234567']) {
      expect(accepted(input)).toBe(true);
      expect(toE164(phoneDigits(input))).toBe('+94771234567');
    }
  });

  it('rejects anything shorter, longer or with another prefix', () => {
    for (const input of [
      '',
      '77123456',
      '7712345678',
      '077123456',
      '07712345678',
      '+9477123456',
      '+947712345678',
      '94771234567',
      '0094771234567',
      '+940771234567',
      '+1771234567',
      '777777777'
    ]) {
      expect(accepted(input), input).toBe(false);
    }
  });

  it('gives the same result typed one key at a time', () => {
    for (const input of ['+94771234567', '0771234567', '771234567']) {
      let field = '';
      for (const key of input) field = phoneDigits(field + key);
      expect(field).toBe('771234567');
    }
  });

  it('never cuts extra digits silently', () => {
    expect(phoneDigits('07712345678')).toBe('7712345678');
  });
});
