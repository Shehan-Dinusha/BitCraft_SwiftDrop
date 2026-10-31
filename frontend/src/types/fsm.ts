import type { DeliveryState } from './contracts';
export const allowedTransitions: Record<DeliveryState, DeliveryState[]> = {
  CREATED: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['PICKED_UP', 'FAILED', 'CANCELLED'],
  PICKED_UP: ['IN_TRANSIT', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  DELIVERED: [],
  FAILED: [],
  CANCELLED: []
};
export const canTransition = (a: DeliveryState, b: DeliveryState) =>
  allowedTransitions[a].includes(b);
