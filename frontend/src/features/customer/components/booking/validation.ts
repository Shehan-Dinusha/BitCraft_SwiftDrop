import type { BookingField, BookingForm } from './bookingForm';

/**
 * Client-side copy of the order service's rules (`CreateOrderRequest` and `ValidationPatterns` in
 * customer-order-service). Keep the two in step: the backend stays the authority, this only stops the
 * customer moving on with a value the server would reject. Messages never repeat the typed value.
 */
const PATTERNS = {
  address: /^[\p{L}\p{M}\p{N} .,/#()&'’:-]*$/u,
  name: /^[\p{L}\p{M} .'’-]*$/u,
  city: /^[\p{L}\p{M}\p{N} .'’-]*$/u,
  postalCode: /^\d{5}$/,
  phoneDigits: /^\d{9}$/,
  freeText: /^[^<>]*$/,
  email: /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/,
  decimal: /^\d+(\.\d+)?$/
};

export const WEIGHT_MAX_KG = 50;

export type BookingErrors = Partial<Record<BookingField, string>>;

export type BookingStepId = 'pickup' | 'dropoff' | 'parcel' | 'review';

export const STEP_FIELDS: Record<BookingStepId, BookingField[]> = {
  pickup: [
    'pickupContactName',
    'pickupContactPhone',
    'pickupContactAlternatePhone',
    'pickupHouse',
    'pickupStreet',
    'pickupArea',
    'pickupLandmark',
    'pickupProvince',
    'pickupCity',
    'pickupDistrict',
    'pickupPostalCode'
  ],
  dropoff: [
    'recipientName',
    'recipientPhone',
    'recipientAlternatePhone',
    'recipientEmail',
    'deliveryHouse',
    'deliveryStreet',
    'deliveryArea',
    'deliveryLandmark',
    'deliveryProvince',
    'deliveryCity',
    'deliveryDistrict',
    'deliveryPostalCode',
    'customerNotes'
  ],
  parcel: [
    'lengthCm',
    'widthCm',
    'heightCm',
    'actualWeightKg',
    'speed',
    'itemDescription',
    'declaredValue',
    'codAmount'
  ],
  review: ['prohibitedItemsDeclared']
};

function text(
  value: string,
  {
    empty,
    min,
    max,
    pattern,
    symbols
  }: { empty?: string; min?: number; max: number; pattern: RegExp; symbols: string }
): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return empty;
  if (min !== undefined && trimmed.length < min) return `Use at least ${min} characters`;
  if (trimmed.length > max) return `Use ${max} characters or fewer`;
  if (!pattern.test(trimmed)) return symbols;
  return undefined;
}

const SYMBOLS = 'Remove symbols such as < > = ; " @';

const personName = (value: string, who: string) =>
  text(value, {
    empty: `Enter the ${who}'s name`,
    min: 2,
    max: 100,
    pattern: PATTERNS.name,
    symbols: 'Use letters, spaces, full stops, apostrophes and hyphens only'
  });
const city = (value: string) =>
  text(value, {
    empty: 'Enter the city or town',
    min: 2,
    max: 100,
    pattern: PATTERNS.city,
    symbols: 'Use letters, numbers and spaces only'
  });

/** An optional second phone: empty is fine, otherwise 9 digits that differ from the main number. */
function alternatePhone(digits: string, main: string) {
  if (!digits) return undefined;
  if (!PATTERNS.phoneDigits.test(digits)) return 'Enter the 9 digits after +94';
  return digits === main ? 'Use a different number from the main phone' : undefined;
}

/** One box of the address; the backend's address rule applies to the joined text. */
function part(
  value: string,
  { label, required, min, max }: { label: string; required: boolean; min?: number; max: number }
) {
  return text(value, {
    empty: required ? `Enter ${label}` : undefined,
    min,
    max,
    pattern: PATTERNS.address,
    symbols: SYMBOLS
  });
}

function postalCode(value: string) {
  if (!value) return 'Enter the 5-digit postal code';
  return PATTERNS.postalCode.test(value) ? undefined : 'Postal codes have 5 digits, e.g. 00300';
}

function phone(digits: string) {
  if (!digits) return 'Enter a mobile or landline number';
  return PATTERNS.phoneDigits.test(digits) ? undefined : 'Enter the 9 digits after +94';
}

function measure(
  value: string,
  label: string,
  { min, max, unit }: { min: number; max?: number; unit: string }
) {
  const trimmed = value.trim();
  if (!trimmed) return `Enter the ${label.toLowerCase()} in ${unit}`;
  if (!PATTERNS.decimal.test(trimmed)) return `Enter the ${label.toLowerCase()} as a number`;
  const parsed = Number(trimmed);
  if (parsed < min) return `${label} must be at least ${min} ${unit}`;
  if (max !== undefined && parsed > max) return `${label} can be up to ${max} ${unit}`;
  return undefined;
}

function optionalAmount(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return PATTERNS.decimal.test(trimmed) ? undefined : 'Enter an amount in rupees, e.g. 2500';
}

function fieldError(field: BookingField, form: BookingForm): string | undefined {
  switch (field) {
    case 'pickupContactName':
      return personName(form.pickupContactName, 'contact');
    case 'recipientName':
      return personName(form.recipientName, 'recipient');
    case 'pickupContactPhone':
    case 'recipientPhone':
      return phone(form[field]);
    case 'pickupContactAlternatePhone':
      return alternatePhone(form.pickupContactAlternatePhone, form.pickupContactPhone);
    case 'pickupHouse':
    case 'deliveryHouse':
      return part(form[field], { label: 'the house or building number', required: true, max: 40 });
    case 'pickupStreet':
    case 'deliveryStreet':
      return part(form[field], { label: 'the street or road', required: true, min: 2, max: 150 });
    case 'pickupArea':
    case 'deliveryArea':
      return part(form[field], { label: 'the area', required: false, max: 98 });
    case 'pickupLandmark':
    case 'deliveryLandmark':
      return part(form[field], { label: 'the landmark', required: false, max: 100 });
    case 'pickupProvince':
      return form.pickupProvince || form.pickupDistrict ? undefined : 'Choose a province';
    case 'deliveryProvince':
      return form.deliveryProvince || form.deliveryDistrict ? undefined : 'Choose a province';
    case 'pickupCity':
    case 'deliveryCity':
      return city(form[field]);
    case 'pickupDistrict':
    case 'deliveryDistrict':
      return form[field] ? undefined : 'Choose a district';
    case 'pickupPostalCode':
    case 'deliveryPostalCode':
      return postalCode(form[field]);
    case 'recipientAlternatePhone':
      return alternatePhone(form.recipientAlternatePhone, form.recipientPhone);
    case 'customerNotes':
      return text(form.customerNotes, {
        max: 500,
        pattern: PATTERNS.freeText,
        symbols: 'Remove the < and > characters'
      });
    case 'recipientEmail': {
      const value = form.recipientEmail.trim();
      if (!value) return 'Enter an email address for delivery updates';
      if (value.length > 128) return 'Use 128 characters or fewer';
      return PATTERNS.email.test(value) ? undefined : 'Enter an email like name@example.com';
    }
    case 'lengthCm':
      return measure(form.lengthCm, 'Length', { min: 0.1, unit: 'cm' });
    case 'widthCm':
      return measure(form.widthCm, 'Width', { min: 0.1, unit: 'cm' });
    case 'heightCm':
      return measure(form.heightCm, 'Height', { min: 0.1, unit: 'cm' });
    case 'actualWeightKg':
      return measure(form.actualWeightKg, 'Weight', { min: 0.01, max: WEIGHT_MAX_KG, unit: 'kg' });
    case 'itemDescription':
      return text(form.itemDescription, {
        max: 512,
        pattern: PATTERNS.freeText,
        symbols: 'Remove the < and > characters'
      });
    case 'declaredValue':
      return optionalAmount(form.declaredValue);
    case 'codAmount': {
      if (!form.collectCash) return undefined;
      const trimmed = form.codAmount.trim();
      if (!trimmed || Number(trimmed) <= 0) return 'Enter the amount the courier should collect';
      return optionalAmount(trimmed);
    }
    case 'prohibitedItemsDeclared':
      return form.prohibitedItemsDeclared
        ? undefined
        : 'Tick this box to confirm the parcel is safe to carry';
    case 'speed':
    case 'collectCash':
      return undefined;
  }
}

export function validateStep(step: BookingStepId, form: BookingForm): BookingErrors {
  const errors: BookingErrors = {};
  for (const field of STEP_FIELDS[step]) {
    const error = fieldError(field, form);
    if (error) errors[field] = error;
  }
  return errors;
}

/** Re-checks a single field, used to clear an error as soon as the value becomes valid. */
export const validateField = fieldError;

export function firstInvalidStep(form: BookingForm): BookingStepId | null {
  const order: BookingStepId[] = ['pickup', 'dropoff', 'parcel', 'review'];
  return order.find((step) => Object.keys(validateStep(step, form)).length > 0) ?? null;
}

/**
 * The order service returns validation failures as `field: message; field: message` with fixed
 * messages (the rejected value is never included). Map the ones that belong to the form.
 */
export function parseServerFieldErrors(message: string | undefined): BookingErrors {
  if (!message) return {};
  const known = new Set<string>(Object.values(STEP_FIELDS).flat());
  const joined: Record<string, BookingField> = {
    pickupAddressLine1: 'pickupStreet',
    pickupAddressLine2: 'pickupArea',
    deliveryAddressLine1: 'deliveryStreet',
    deliveryAddressLine2: 'deliveryArea'
  };
  const errors: BookingErrors = {};
  for (const part of message.split(';')) {
    const separator = part.indexOf(':');
    if (separator < 0) continue;
    const name = part.slice(0, separator).trim();
    const field = joined[name] ?? name;
    const text = part.slice(separator + 1).trim();
    if (known.has(field) && text) errors[field as BookingField] = text;
  }
  return errors;
}

export function stepOfField(field: BookingField): BookingStepId {
  const entry = (Object.entries(STEP_FIELDS) as [BookingStepId, BookingField[]][]).find(
    ([, fields]) => fields.includes(field)
  );
  return entry ? entry[0] : 'pickup';
}
