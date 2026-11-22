// @vitest-environment node
import { createHmac, timingSafeEqual } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { phoneDigits, phoneProblem } from '@/utils/phone';
import { ORDER_REQUEST, STATUS_EVENT, VERIFY_CODE, pretty } from './apiGuide';

type Verify = (rawBody: Buffer, headers: Record<string, string>, secret: string) => boolean;

/** Runs the code the guide tells merchants to copy. */
const isFromSwiftDrop = new Function(
  'require',
  `${VERIFY_CODE}\nreturn isFromSwiftDrop;`
)(() => ({ createHmac, timingSafeEqual })) as Verify;

const secret = 'whsec_0123456789abcdef';
const body = Buffer.from(pretty(STATUS_EVENT));
/** Signed the way WebhookSigner does: HMAC-SHA256 over "<unix seconds>." and the raw body. */
function signed(at = Math.floor(Date.now() / 1000)) {
  const hex = createHmac('sha256', secret).update(`${at}.`).update(body).digest('hex');
  return { 'x-swiftdrop-timestamp': String(at), 'x-swiftdrop-signature': `sha256=${hex}` };
}

describe('API guide', () => {
  it('shows verification code that accepts a real signature', () => {
    expect(isFromSwiftDrop(body, signed(), secret)).toBe(true);
  });

  it('shows verification code that refuses a changed body, another secret, an old message or no headers', () => {
    expect(isFromSwiftDrop(Buffer.from(`${body} `), signed(), secret)).toBe(false);
    expect(isFromSwiftDrop(body, signed(), 'whsec_other')).toBe(false);
    expect(isFromSwiftDrop(body, signed(Math.floor(Date.now() / 1000) - 600), secret)).toBe(false);
    expect(isFromSwiftDrop(body, {}, secret)).toBe(false);
  });

  it('shows an order the API accepts', () => {
    for (const { contactPhone, postalCode } of [ORDER_REQUEST.pickup, ORDER_REQUEST.delivery]) {
      expect(phoneProblem(phoneDigits(contactPhone))).toBeUndefined();
      expect(postalCode).toMatch(/^\d{5}$/);
    }
    expect(ORDER_REQUEST.externalRef).toMatch(/^[A-Za-z0-9._:/#-]{1,128}$/);
    expect(ORDER_REQUEST.parcel.prohibitedItemsDeclared).toBe(true);
  });
});
