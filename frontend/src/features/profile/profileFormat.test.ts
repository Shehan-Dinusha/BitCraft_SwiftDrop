import { describe, expect, it } from 'vitest';
import {
  PROFILE_PATH,
  displayName,
  districtLabel,
  passwordProblem,
  roleLabel,
  toUpdateRequest,
  validateProfile
} from './profileFormat';

const valid = { firstName: 'Nimal', lastName: 'Perera', phone: '', district: 'COLOMBO' };

describe('profile helpers', () => {
  it('shows the full name, falling back to the username', () => {
    expect(displayName({ firstName: 'Nimal', lastName: 'Perera', username: 'nimal' })).toBe('Nimal Perera');
    expect(displayName({ firstName: '', lastName: '', username: 'nimal' })).toBe('nimal');
  });

  it('has a profile page per workspace', () => {
    expect(PROFILE_PATH).toEqual({
      CUSTOMER: '/customer/profile',
      COURIER: '/courier/profile',
      MERCHANT: '/merchant/profile',
      ADMIN_OPS: '/ops/profile'
    });
  });

  it('labels roles and districts in plain words', () => {
    expect(roleLabel('SYSTEM_ADMIN')).toBe('System admin');
    expect(districtLabel('NUWARA_ELIYA')).toBe('Nuwara Eliya');
  });

  it('validates with the same rules as the server', () => {
    expect(validateProfile(valid)).toEqual({});
    // The district is not required: customers and merchants have none.
    expect(validateProfile({ ...valid, firstName: ' ', district: '' })).toEqual({
      firstName: 'Enter your first name'
    });
    expect(validateProfile({ ...valid, phone: '12' }).phone).toBeDefined();
    expect(validateProfile({ ...valid, phone: '771234567' })).toEqual({});
    expect(validateProfile({ ...valid, phone: '7712345678' }).phone).toBeDefined();
  });

  it('sends trimmed names and the phone with +94', () => {
    expect(toUpdateRequest({ firstName: ' Nimal ', lastName: 'Perera ', phone: '771234567', district: 'KANDY' })).toEqual({
      firstName: 'Nimal',
      lastName: 'Perera',
      phone: '+94771234567',
      district: 'KANDY'
    });
  });

  it('checks a new password like the server', () => {
    expect(passwordProblem('short1', 'old', 'short1')).toBe('Use 8 to 72 characters');
    expect(passwordProblem('lettersonly', 'old', 'lettersonly')).toMatch(/letter and one number/);
    expect(passwordProblem('Secret123', 'Secret123', 'Secret123')).toMatch(/different/);
    expect(passwordProblem('Secret123', 'old', 'Secret124')).toBe('The passwords do not match');
    expect(passwordProblem('Secret123', 'old', 'Secret123')).toBeUndefined();
  });
});
