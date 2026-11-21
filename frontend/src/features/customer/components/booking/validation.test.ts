import { describe, expect, it } from 'vitest';
import { initialBookingForm, type BookingForm } from './bookingForm';
import {
  firstInvalidStep,
  parseServerFieldErrors,
  stepOfField,
  validateField,
  validateStep
} from './validation';

const validForm: BookingForm = {
  ...initialBookingForm,
  pickupContactName: 'Nimal Perera',
  pickupContactPhone: '771234567',
  pickupHouse: '12',
  pickupStreet: 'Galle Road',
  pickupCity: 'Colombo',
  pickupDistrict: 'COLOMBO',
  pickupPostalCode: '00300',
  recipientName: 'Kamala Silva',
  recipientPhone: '712345678',
  recipientEmail: 'kamala@example.com',
  deliveryHouse: '5A',
  deliveryStreet: 'Kandy Road',
  deliveryCity: 'Kandy',
  deliveryDistrict: 'KANDY',
  deliveryPostalCode: '20000',
  lengthCm: '20',
  widthCm: '15',
  heightCm: '10',
  actualWeightKg: '2.5',
  prohibitedItemsDeclared: true
};

describe('booking validation', () => {
  it('asks for the province first, unless a district already settles it', () => {
    expect(validateField('pickupProvince', { ...validForm, pickupDistrict: '' })).toBe(
      'Choose a province'
    );
    expect(validateField('pickupProvince', validForm)).toBeUndefined();
    expect(
      validateField('deliveryProvince', {
        ...validForm,
        deliveryDistrict: '',
        deliveryProvince: 'CENTRAL'
      })
    ).toBeUndefined();
  });

  it('accepts a complete form', () => {
    expect(firstInvalidStep(validForm)).toBeNull();
  });

  it('reports the first step that still has errors', () => {
    expect(firstInvalidStep(initialBookingForm)).toBe('pickup');
    expect(firstInvalidStep({ ...validForm, recipientEmail: '' })).toBe('dropoff');
    expect(firstInvalidStep({ ...validForm, prohibitedItemsDeclared: false })).toBe('review');
  });

  it('requires 9-digit phone numbers and 5-digit postal codes', () => {
    const errors = validateStep('pickup', {
      ...validForm,
      pickupContactPhone: '12345',
      pickupPostalCode: '300'
    });
    expect(errors.pickupContactPhone).toBe('Enter the 9 digits after +94');
    expect(errors.pickupPostalCode).toContain('5 digits');
  });

  it('rejects an alternate phone equal to the main phone', () => {
    expect(
      validateField('pickupContactAlternatePhone', {
        ...validForm,
        pickupContactAlternatePhone: validForm.pickupContactPhone
      })
    ).toContain('different number');
  });

  it('blocks markup characters in free text', () => {
    expect(validateField('customerNotes', { ...validForm, customerNotes: '<script>' })).toContain(
      '< and >'
    );
  });

  it('enforces the parcel weight range', () => {
    expect(validateField('actualWeightKg', { ...validForm, actualWeightKg: '0' })).toContain(
      'at least'
    );
    expect(validateField('actualWeightKg', { ...validForm, actualWeightKg: '51' })).toContain(
      'up to 50'
    );
    expect(validateField('actualWeightKg', { ...validForm, actualWeightKg: 'heavy' })).toContain(
      'as a number'
    );
  });

  it('maps server field errors, including joined address fields', () => {
    expect(
      parseServerFieldErrors(
        'pickupAddressLine1: Street is too long; recipientPhone: Invalid phone; unknownField: x; garbage'
      )
    ).toEqual({ pickupStreet: 'Street is too long', recipientPhone: 'Invalid phone' });
    expect(parseServerFieldErrors(undefined)).toEqual({});
  });

  it('finds the step that owns a field', () => {
    expect(stepOfField('recipientEmail')).toBe('dropoff');
    expect(stepOfField('prohibitedItemsDeclared')).toBe('review');
  });
});
