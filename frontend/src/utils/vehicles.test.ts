import { describe, expect, it } from 'vitest';
import { isValidPlate, normalisePlate, vehicleLabel } from './vehicles';

describe('number plates', () => {
  it('normalises spacing, case and the dash like the backend does', () => {
    expect(normalisePlate(' wp  bab 1234 ')).toBe('WP BAB-1234');
    expect(normalisePlate('cp kx - 9876')).toBe('CP KX-9876');
    expect(normalisePlate('65-1234')).toBe('65-1234');
  });

  it('accepts current and older Sri Lankan formats and rejects other text', () => {
    expect(isValidPlate('WP BAB-1234')).toBe(true);
    expect(isValidPlate('cab 1234')).toBe(true);
    expect(isValidPlate('250-1234')).toBe(true);
    expect(isValidPlate('hello')).toBe(false);
    expect(isValidPlate('WP BAB-12')).toBe(false);
  });
});

describe('vehicleLabel', () => {
  it('names each supported type', () => {
    expect(vehicleLabel('THREE_WHEELER')).toBe('Three-wheeler');
    expect(vehicleLabel('LORRY')).toBe('Lorry');
    expect(vehicleLabel(undefined)).toBe('Vehicle');
  });
});
