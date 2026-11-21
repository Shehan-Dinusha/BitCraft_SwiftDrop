import { describe, expect, it } from 'vitest';
import { DISTRICTS, districtsOf, PROVINCES, provinceLabelOf, provinceOf } from './bookingForm';

describe('province follows the district', () => {
  it('gives the province of a district', () => {
    expect(provinceOf('COLOMBO')).toBe('WESTERN');
    expect(provinceOf('ANURADHAPURA')).toBe('NORTH_CENTRAL');
    expect(provinceLabelOf('ANURADHAPURA')).toBe('North Central');
  });

  it('is empty until a district is chosen', () => {
    expect(provinceOf('')).toBe('');
    expect(provinceLabelOf('')).toBe('');
    expect(provinceLabelOf(undefined)).toBe('');
  });

  it('has a province for every district, and no district twice', () => {
    for (const { value } of DISTRICTS) {
      expect(provinceOf(value), value).not.toBe('');
    }
    const listed = PROVINCES.flatMap((province) => province.districts);
    expect(new Set(listed).size).toBe(listed.length);
    expect(listed).toHaveLength(DISTRICTS.length);
  });

  it('never pairs Anuradhapura with Colombo', () => {
    expect(provinceOf('ANURADHAPURA')).not.toBe(provinceOf('COLOMBO'));
  });
});

describe('the district list follows the province', () => {
  it('lists only the districts of the chosen province', () => {
    expect(districtsOf('UVA').map((d) => d.value)).toEqual(['BADULLA', 'MONARAGALA']);
    expect(districtsOf('NORTHERN')).toHaveLength(5);
  });

  it('is empty until a province is chosen', () => {
    expect(districtsOf('')).toEqual([]);
  });
});
