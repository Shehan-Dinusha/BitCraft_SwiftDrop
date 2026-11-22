import type { DeliveryFailureReason, DeliveryState } from '@/services/api/deliveryApi';

/** The reasons a person may have chosen, older ones included. */
const LABELS: Record<string, string> = {
  RECIPIENT_UNAVAILABLE: 'Nobody there',
  CUSTOMER_UNAVAILABLE: 'Nobody there',
  INCORRECT_ADDRESS: 'Wrong or incomplete address',
  WRONG_ADDRESS: 'Wrong or incomplete address',
  ACCESS_RESTRICTED: "Couldn't get access",
  RECIPIENT_REJECTED: 'Recipient refused it',
  REFUSED_BY_CUSTOMER: 'Recipient refused it',
  DAMAGED_IN_TRANSIT: 'Parcel damaged',
  DAMAGED: 'Parcel damaged',
  LOST: 'Parcel lost',
  ADVERSE_WEATHER: 'Weather or road blocked',
  WEATHER_DELAY: 'Weather or road blocked',
  PAYMENT_FAILED: 'Would not pay',
  OTHER: 'Other'
};

export const failureLabel = (reason: string | null | undefined) =>
  reason ? (LABELS[reason] ?? reason) : 'No reason given';

/** SwiftDrop lost or damaged it, so it is neither delivered nor returned. */
export const LOSS_REASONS: DeliveryFailureReason[] = ['DAMAGED_IN_TRANSIT', 'LOST'];
const isLoss = (reason: DeliveryFailureReason) => LOSS_REASONS.includes(reason);

const PICKUP: DeliveryFailureReason[] = [
  'RECIPIENT_UNAVAILABLE',
  'INCORRECT_ADDRESS',
  'ACCESS_RESTRICTED',
  'PAYMENT_FAILED',
  'ADVERSE_WEATHER',
  'OTHER'
];
const DELIVERY: DeliveryFailureReason[] = [
  'RECIPIENT_UNAVAILABLE',
  'INCORRECT_ADDRESS',
  'ACCESS_RESTRICTED',
  'RECIPIENT_REJECTED',
  'ADVERSE_WEATHER',
  ...LOSS_REASONS,
  'OTHER'
];

const delivering = (state: DeliveryState, viaHubs: boolean) =>
  state === 'OUT_FOR_DELIVERY' || (!viaHubs && (state === 'PICKED_UP' || state === 'IN_TRANSIT'));

/** What a courier may report, by where they are. Between the two doors only a loss counts. */
export function failureReasons(state: DeliveryState, viaHubs: boolean): DeliveryFailureReason[] {
  if (state === 'ASSIGNED') return PICKUP;
  return delivering(state, viaHubs) ? DELIVERY : LOSS_REASONS;
}

export function failureTitle(state: DeliveryState, viaHubs: boolean): string {
  if (state === 'ASSIGNED') return "Can't collect it";
  return delivering(state, viaHubs) ? "Can't deliver" : 'Lost or damaged';
}

/** A failed attempt goes back to the hub; a failed pickup, a loss or damage is final. */
export const failureTarget = (
  state: DeliveryState,
  reason: DeliveryFailureReason
): DeliveryState => (state === 'ASSIGNED' || isLoss(reason) ? 'FAILED' : 'ATTEMPT_FAILED');
