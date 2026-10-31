export const DELIVERY_STATES = [
  'CREATED',
  'ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'FAILED',
  'CANCELLED'
] as const;
export type DeliveryState = (typeof DELIVERY_STATES)[number];
export const PERSONA_ROLES = ['CUSTOMER', 'COURIER', 'MERCHANT', 'ADMIN_OPS'] as const;
export type PersonaRole = (typeof PERSONA_ROLES)[number];
export type OrderCreatedEvent = {
  eventId: string;
  correlationId: string;
  orderId: string;
  customerId: string;
  merchantId: string;
  pickupAddress: string;
  deliveryAddress: string;
  estimatedPrice: number;
  currency: string;
  timestamp: string;
};
export type DeliveryStateChangedEvent = {
  eventId: string;
  correlationId: string;
  deliveryId: string;
  orderId: string;
  courierId: string;
  previousState: DeliveryState;
  newState: DeliveryState;
  notes: string;
  timestamp: string;
};
export type LocationUpdatedEvent = {
  eventId: string;
  correlationId: string;
  deliveryId: string;
  courierId: string;
  latitude: number;
  longitude: number;
  bearingDegrees: number;
  speedKmh: number;
  accuracy: number;
  timestamp: string;
};

/** Mirrors `PublicOrderStatus` in the generated api-gateway / customer-order-service types. */
export const PUBLIC_ORDER_STATUSES = [
  'CREATED',
  'PICKUP_ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'FAILED',
  'DELIVERED',
  'CANCELLED',
  'RETURNED_TO_SENDER'
] as const;
export type PublicOrderStatus = (typeof PUBLIC_ORDER_STATUSES)[number];
