import type {
  CourierSuggestionResponse,
  DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import { colomboClock } from '@/utils/colomboDate';

/** `12`, `2.5`: one decimal at most, with thousands separators. */
export const amount = (value?: number) =>
  (value ?? 0).toLocaleString('en-US', { maximumFractionDigits: 1 });

/** `30 × 20 × 10 cm · 2.5 kg`, or undefined for an order booked before sizes were recorded. */
export function parcelSize(order: DispatchAssignmentResponse): string | undefined {
  const { lengthCm, widthCm, heightCm, weightKg } = order;
  if (lengthCm == null || widthCm == null || heightCm == null || weightKg == null) return undefined;
  return `${amount(lengthCm)} × ${amount(widthCm)} × ${amount(heightCm)} cm · ${amount(weightKg)} kg`;
}

/** `On break until 13:15` while a courier is on lunch, otherwise undefined. They can still be given parcels. */
export const onBreakUntil = (breakEndsAt?: string | null) =>
  breakEndsAt ? `On break until ${colomboClock(breakEndsAt)}` : undefined;

/** Why a courier cannot take the parcel, in the dispatcher's words. Undefined when they can. */
export function blockedBecause(courier: CourierSuggestionResponse): string | undefined {
  const vehicle = vehicleLabel(courier.vehicleType).toLowerCase();
  switch (courier.ineligibleReason) {
    case undefined:
    case null:
      return courier.eligible === false ? 'Not available' : undefined;
    case 'OFF_SHIFT':
      return 'Off shift';
    case 'ON_RUN':
      return 'Left the hub';
    case 'PICKUPS_CLOSED':
      return 'Pickups closed for today';
    case 'TOO_LONG':
      return `Too long for a ${vehicle} (up to ${amount(courier.maxSideCm)} cm)`;
    case 'TOO_HEAVY':
      return `Too heavy for a ${vehicle} (up to ${amount(courier.maxWeightKg)} kg)`;
    case 'TOO_BIG':
      return `Too big for a ${vehicle} (up to ${amount(courier.maxVolumeLitres)} L)`;
    case 'PARCEL_LIMIT':
      return 'Parcel limit reached';
    case 'WEIGHT_LIMIT':
      return 'Not enough weight left';
    case 'VOLUME_LIMIT':
      return 'Not enough space left';
  }
}
