import type { DeliveryState } from './contracts';

/**
 * Mirrors the delivery service's state machine. A same-district parcel goes PICKED_UP -> IN_TRANSIT ->
 * OUT_FOR_DELIVERY; a cross-district one goes through both hubs: PICKED_UP -> AT_ORIGIN_HUB ->
 * IN_TRANSIT (marked Outbound) -> AT_DESTINATION_HUB (accepted) -> OUT_FOR_DELIVERY.
 */
export const allowedTransitions: Record<DeliveryState, DeliveryState[]> = {
  CREATED: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['PICKED_UP', 'FAILED', 'CANCELLED'],
  PICKED_UP: ['IN_TRANSIT', 'AT_ORIGIN_HUB', 'FAILED'],
  AT_ORIGIN_HUB: ['IN_TRANSIT', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'AT_DESTINATION_HUB', 'FAILED'],
  AT_DESTINATION_HUB: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  DELIVERED: [],
  FAILED: [],
  CANCELLED: []
};
export const canTransition = (a: DeliveryState, b: DeliveryState) =>
  allowedTransitions[a].includes(b);

/** Picked up in one district and delivered in another, so it goes through both districts' hubs. */
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

/** Still work for the courier: not finished, and not handed in at the hub (the end of their leg). */
export const isOpenForCourier = (state: string | null | undefined) =>
  state !== 'DELIVERED' && state !== 'FAILED' && state !== 'CANCELLED' && state !== 'AT_ORIGIN_HUB';

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
    default:
      return null;
  }
}
