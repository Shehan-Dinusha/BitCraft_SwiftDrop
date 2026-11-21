import { describe, expect, it } from 'vitest';
import type { SavedAddress } from '@/services/api/orderApi';
import { initialBookingForm } from './bookingForm';
import { validateField } from './validation';
import {
  addressSummary,
  applySavedAddress,
  toSavedAddressRequest,
  validateAddressLabel
} from './savedAddresses';

const saved: SavedAddress = {
  addressId: 'a-1',
  label: 'Home',
  contactName: 'Nimal Perera',
  contactPhone: '+94771234567',
  house: '45/2',
  street: 'Temple Road',
  landmark: 'Near the post office',
  city: 'Nugegoda',
  district: 'COLOMBO',
  postalCode: '10250'
};

describe('saved addresses', () => {
  it('fills the pickup side, province included, and back again', () => {
    const form = applySavedAddress(initialBookingForm, 'pickup', saved);
    expect(form.pickupContactPhone).toBe('771234567');
    expect(form.pickupProvince).toBe('WESTERN');
    expect(form.pickupDistrict).toBe('COLOMBO');
    expect(toSavedAddressRequest(form, 'pickup', ' Home ')).toMatchObject({
      label: 'Home',
      contactPhone: '+94771234567',
      house: '45/2',
      area: undefined,
      landmark: 'Near the post office',
      postalCode: '10250'
    });
  });

  it('fills the recipient on the drop-off side and keeps the typed email', () => {
    const form = applySavedAddress({ ...initialBookingForm, recipientEmail: 'k@example.com' }, 'delivery', saved);
    expect(form.recipientName).toBe('Nimal Perera');
    expect(form.deliveryCity).toBe('Nugegoda');
    expect(form.recipientEmail).toBe('k@example.com');
  });

  it('summarises an address and checks its name', () => {
    expect(addressSummary(saved)).toBe('45/2, Temple Road, Nugegoda 10250');
    expect(validateAddressLabel('')).toBeDefined();
    expect(validateAddressLabel('H')).toBeDefined();
    expect(validateAddressLabel('<Home>')).toBeDefined();
    expect(validateAddressLabel("Amma's place")).toBeUndefined();
  });
});

describe('practical inputs', () => {
  it('rejects phone numbers that cannot be real', () => {
    const form = { ...initialBookingForm };
    expect(validateField('pickupContactPhone', { ...form, pickupContactPhone: '771234567' })).toBeUndefined();
    expect(validateField('pickupContactPhone', { ...form, pickupContactPhone: '777777777' })).toBeDefined();
    expect(validateField('pickupContactPhone', { ...form, pickupContactPhone: '071234567' })).toBeDefined();
    expect(validateField('pickupContactPhone', { ...form, pickupContactPhone: '77123' })).toBeDefined();
  });

  it('needs letters or numbers in each address box', () => {
    const form = { ...initialBookingForm };
    expect(validateField('pickupHouse', { ...form, pickupHouse: '--' })).toBeDefined();
    expect(validateField('pickupHouse', { ...form, pickupHouse: '45/2' })).toBeUndefined();
    expect(validateField('pickupLandmark', { ...form, pickupLandmark: '' })).toBeUndefined();
  });
});
