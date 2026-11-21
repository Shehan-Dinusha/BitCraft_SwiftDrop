import { describe, expect, it } from 'vitest';
import {
  emptyApplication,
  PROOF_REQUIRED,
  toRequest,
  validateApplication,
  type ApplicationForm
} from './merchantApplication';

const valid: ApplicationForm = {
  ...emptyApplication('orders@lankacrafts.lk', 'COLOMBO'),
  businessName: 'Lanka Crafts',
  businessCategory: 'GIFTS_AND_CRAFTS',
  monthlyParcelVolume: 'UP_TO_50',
  websiteUrl: 'facebook.com/lankacrafts',
  contactPhone: '771234567',
  pickupAddress: 'No. 12, Temple Road',
  pickupCity: 'Nugegoda',
  pickupPostalCode: '10250'
};

describe('merchant application validation', () => {
  it('accepts a small seller with a social page and no registration number', () => {
    expect(validateApplication(valid)).toEqual({});
  });

  it('needs a registration number or a website', () => {
    const errors = validateApplication({ ...valid, websiteUrl: '', businessRegNo: '  ' });
    expect(errors.websiteUrl).toBe(PROOF_REQUIRED);
    expect(validateApplication({ ...valid, websiteUrl: '', businessRegNo: 'PV 00123456' })).toEqual(
      {}
    );
  });

  it('flags each invalid field with its own message', () => {
    const errors = validateApplication({
      ...emptyApplication(),
      businessName: '<b>',
      businessRegNo: 'PV#1',
      websiteUrl: 'not a link',
      contactEmail: 'shop@',
      contactPhone: '7712',
      pickupAddress: 'abc',
      pickupCity: 'C',
      pickupPostalCode: '1025'
    });
    expect(Object.keys(errors).sort()).toEqual(
      [
        'businessCategory',
        'businessName',
        'businessRegNo',
        'contactEmail',
        'contactPhone',
        'monthlyParcelVolume',
        'operatingDistrict',
        'pickupAddress',
        'pickupCity',
        'pickupPostalCode',
        'websiteUrl'
      ].sort()
    );
  });

  it('builds the request with +94 and without empty optional fields', () => {
    const request = toRequest({ ...valid, businessName: '  Lanka Crafts ' });
    expect(request.contactPhone).toBe('+94771234567');
    expect(request.businessName).toBe('Lanka Crafts');
    expect(request.businessRegNo).toBeUndefined();
    expect(request.websiteUrl).toBe('facebook.com/lankacrafts');
  });
});
