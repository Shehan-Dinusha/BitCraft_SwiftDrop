import type { VehicleType } from '@/services/api/dispatchApi';

/** Courier vehicle types, in the order forms list them, with their default parcel capacity. */
export const VEHICLE_TYPES: { value: VehicleType; label: string; capacity: number }[] = [
  { value: 'MOTORCYCLE', label: 'Motorcycle', capacity: 5 },
  { value: 'THREE_WHEELER', label: 'Three-wheeler', capacity: 10 },
  { value: 'VAN', label: 'Van', capacity: 25 },
  { value: 'LORRY', label: 'Lorry', capacity: 50 }
];

export function vehicleLabel(type?: VehicleType | null): string {
  return VEHICLE_TYPES.find((vehicle) => vehicle.value === type)?.label ?? 'Vehicle';
}

/**
 * Sri Lankan number plate as the backend accepts it: optional province, letters or the older numeric
 * series, a dash and four digits (WP BAB-1234, CAB-1234, 65-1234). Mirrors CourierProfileService.
 */
export function normalisePlate(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .replace(/\s+/g, ' ')
    .replace(/\s*-\s*/g, '-')
    .replace(/^(.*[A-Z0-9]) (\d{4})$/, '$1-$2');
}

const PLATE = /^(?:[A-Z]{2} )?(?:[A-Z]{1,3}|\d{1,3})-\d{4}$/;

export function isValidPlate(raw: string): boolean {
  return PLATE.test(normalisePlate(raw));
}
