/**
 * The one phone rule, the same as `SriLankanPhone` in common-contracts: +94 and 9 digits, 0 and 9 digits,
 * or just the 9 digits (never starting with 0). Forms hold only the 9 digits, because the phone input
 * shows +94 in front; requests send +94 and the digits.
 */
const DIGITS = /^[1-9]\d{8}$/;

export const PHONE_HINT = '9 digits after +94';

/**
 * What the person typed or pasted, or a number from the server, as the digits after +94. A leading 0 or
 * +94 is dropped. Extra digits are kept, so a number that is too long is reported instead of cut.
 */
export function phoneDigits(input: string): string {
  const value = input.replace(/[^\d+]/g, '').replace(/(?!^)\+/g, '');
  return value.startsWith('+94') ? value.slice(3) : value.replace(/^0/, '');
}

/** Why the digits are not a phone number, or undefined when they are. */
export function phoneProblem(digits: string): string | undefined {
  if (!DIGITS.test(digits)) return 'Enter the 9 digits after +94';
  if (/^(\d)\1+$/.test(digits)) return 'Enter a real phone number';
  return undefined;
}

export const toE164 = (digits: string) => `+94${digits}`;

/** A stored number for reading: +94 77 123 4567. Anything that is not a phone number is shown as it is. */
export function formatPhone(value: string): string {
  const digits = phoneDigits(value);
  if (!DIGITS.test(digits)) return value;
  return `+94 ${digits.slice(0, 2)} ${digits.slice(2, 5)} ${digits.slice(5)}`;
}
