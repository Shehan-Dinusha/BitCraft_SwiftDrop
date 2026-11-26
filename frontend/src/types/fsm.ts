import type { DeliveryState } from './contracts';

/**
 * Mirrors the delivery service's state machine. A same-district parcel goes PICKED_UP -> IN_TRANSIT ->
 * OUT_FOR_DELIVERY; a cross-district one goes through both hubs: PICKED_UP -> AT_ORIGIN_HUB ->
 * IN_TRANSIT (marked Outbound) -> AT_DESTINATION_HUB (accepted) -> OUT_FOR_DELIVERY. A failed attempt goes
 * back to the hub (HELD_AT_HUB) and from there out again, or back to the sender. FAILED is final.
 */
export const allowedTransitions: Record<DeliveryState, DeliveryState[]> = {
  CREATED: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['PICKED_UP', 'FAILED', 'CANCELLED'],
  PICKED_UP: ['IN_TRANSIT', 'AT_ORIGIN_HUB', 'ATTEMPT_FAILED', 'FAILED'],
  AT_ORIGIN_HUB: ['IN_TRANSIT', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'AT_DESTINATION_HUB', 'ATTEMPT_FAILED', 'FAILED'],
  AT_DESTINATION_HUB: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'ATTEMPT_FAILED', 'FAILED'],
  DELIVERED: [],
  ATTEMPT_FAILED: ['HELD_AT_HUB', 'FAILED'],
  HELD_AT_HUB: ['AT_DESTINATION_HUB', 'RETURN_IN_TRANSIT', 'RETURN_AT_HUB', 'FAILED'],
  RETURN_IN_TRANSIT: ['RETURN_AT_HUB', 'FAILED'],
  RETURN_AT_HUB: ['OUT_FOR_RETURN', 'FAILED'],
  OUT_FOR_RETURN: ['RETURNED', 'RETURN_AT_HUB', 'FAILED'],
  RETURNED: [],
  FAILED: [],
  CANCELLED: []
};
export const canTransition = (a: DeliveryState, b: DeliveryState) =>
  allowedTransitions[a].includes(b);

/** Picked up in one district and delivered in another, so it goes through both districts' hubs. */
/**
 * Handed in at a hub after pickup: Standard and Express, and anything going to another district. Only Same day
 * within one district goes straight to the recipient. The server says which (`viaHub`); older records fall back
 * to the districts.
 */
export function goesViaHub(route: {
  viaHub?: boolean | null;
  pickupDistrict?: string | null;
  deliveryDistrict?: string | null;
}): boolean {
  return route.viaHub ?? crossesDistricts(route);
}

export function crossesDistricts(route: {
  pickupDistrict?: string | null;
  deliveryDistrict?: string | null;
}): boolean {
  return Boolean(
    route.pickupDistrict &&
    route.deliveryDistrict &&
    route.pickupDistrict !== route.deliveryDistrict
  );
}

/** Nothing more happens to the parcel. */
export const isFinished = (state: string | null | undefined) =>
  state === 'DELIVERED' || state === 'RETURNED' || state === 'FAILED' || state === 'CANCELLED';

/** Still work for the courier: not finished, and not handed in at the hub (the end of their leg). */
export const isOpenForCourier = (state: string | null | undefined) =>
  !isFinished(state) && state !== 'AT_ORIGIN_HUB';

/**
 * The step a courier takes next, by route. Couriers never take the hub steps between the legs
 * (Outbound and accepting at the drop-off hub belong to the dispatchers), so none is offered for them.
 */
export function courierNextStep(
  state: DeliveryState | null | undefined,
  viaHubs: boolean
): DeliveryState | null {
  switch (state) {
    case 'ASSIGNED':
      return 'PICKED_UP';
    case 'PICKED_UP':
      return viaHubs ? 'AT_ORIGIN_HUB' : 'IN_TRANSIT';
    case 'IN_TRANSIT':
      return viaHubs ? null : 'OUT_FOR_DELIVERY';
    case 'AT_DESTINATION_HUB':
      return 'OUT_FOR_DELIVERY';
    case 'OUT_FOR_DELIVERY':
      return 'DELIVERED';
    case 'ATTEMPT_FAILED':
      return 'HELD_AT_HUB';
    case 'RETURN_AT_HUB':
      return 'OUT_FOR_RETURN';
    case 'OUT_FOR_RETURN':
      return 'RETURNED';
    default:
      return null;
  }
}
