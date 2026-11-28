import type { Speed, StatusProgressStatus } from '@/design-system';
import type { CustomerOrderResponse } from '@/services/api/orderApi';
import type { PublicOrderStatus } from '@/types/contracts';

export type { PublicOrderStatus };

export const orderStatus = (order: CustomerOrderResponse): PublicOrderStatus =>
  order.status ?? 'CREATED';

/** Maps the order's speed onto the design system's `SpeedTag` values. */
export const toSpeed = (speed: CustomerOrderResponse['orderSpeed']): Speed =>
  speed === 'EXPRESS' ? 'express' : speed === 'SAME_DAY' ? 'same-day' : 'standard';

/** Customer-facing service names. */
export const SPEED_LABEL: Record<NonNullable<CustomerOrderResponse['orderSpeed']>, string> = {
  STANDARD: 'Standard',
  EXPRESS: 'Express',
  SAME_DAY: 'Same day'
};

/** The fee the customer pays: the final fee once set, otherwise the estimate. */
/**
 * Shown wherever the fee is. The sender pays the courier in cash at pickup (after any re-measure),
 * matching the landing page. The app takes no online payment.
 */
export const PAYMENT_NOTE = 'Pay the courier in cash at pickup.';
export const PAID_NOTE = 'Paid to the courier in cash at pickup.';

/**
 * Whether the sender has paid. A courier cannot pick a parcel up without confirming the exact fee in cash,
 * so every status from pickup on means it was paid.
 */
export const feePaid = (order: CustomerOrderResponse) =>
  !['CREATED', 'PICKUP_ASSIGNED', 'CANCELLED'].includes(orderStatus(order));

export const payableFee = (order: CustomerOrderResponse) =>
  order.finalFee ?? order.estimatedFee ?? 0;

/**
 * Fee to show in lists. A customer cancel leaves `finalFee` untouched, so a cancelled parcel shows
 * no amount rather than one that reads like a charge.
 */
export const listedFee = (order: CustomerOrderResponse) =>
  orderStatus(order) === 'CANCELLED' ? 0 : payableFee(order);

/** Customer-facing status names; the badge's default is the raw status code. */
export const STATUS_LABEL: Record<PublicOrderStatus, string> = {
  CREATED: 'Booked',
  PICKUP_ASSIGNED: 'Courier assigned',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERY_ATTEMPTED: 'Not delivered yet',
  RETURNING: 'Coming back to you',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  FAILED: 'Delivery failed',
  RETURNED_TO_SENDER: 'Returned to you'
};

export const isActive = (order: CustomerOrderResponse) =>
  !['DELIVERED', 'CANCELLED', 'FAILED', 'RETURNED_TO_SENDER'].includes(orderStatus(order));

/** One line telling the customer what happens next, or what they need to do. */
export function nextStep(order: CustomerOrderResponse): string | null {
  switch (orderStatus(order)) {
    case 'CREATED':
      return 'We are finding a courier. Print the shipping label and attach it to the parcel.';
    case 'PICKUP_ASSIGNED':
      return 'A courier is coming to collect it. Have the parcel ready with the label attached.';
    case 'PICKED_UP':
      return 'The courier has your parcel.';
    case 'IN_TRANSIT':
      return order.deliveryCity
        ? `Your parcel is on its way to ${order.deliveryCity}.`
        : 'Your parcel is on its way.';
    case 'OUT_FOR_DELIVERY':
      return order.recipientName
        ? `Out for delivery to ${order.recipientName}.`
        : 'Your parcel is out for delivery.';
    case 'DELIVERY_ATTEMPTED':
      return 'We could not deliver it. We try again, or bring it back to you for free.';
    default:
      return null;
  }
}

export const CANCEL_REASONS = [
  'Booked by mistake',
  'Wrong address or details',
  'No longer need to send it',
  'Other'
] as const;
export type CancelReason = (typeof CANCEL_REASONS)[number];

/**
 * A customer may cancel only a parcel that is still `CREATED`. The order service enforces the same
 * rule, so this only decides whether to show the button.
 */
export const canCancel = (order: CustomerOrderResponse) => orderStatus(order) === 'CREATED';

export const hasPendingFeeRevision = (order: CustomerOrderResponse) =>
  order.feeRevisionStatus === 'PENDING' &&
  order.revisedFee !== undefined &&
  order.revisedFee !== null;

export type StatusGroup = 'all' | 'active' | 'delivered' | 'closed';

export const STATUS_GROUPS: { id: StatusGroup; label: string; statuses: PublicOrderStatus[] }[] = [
  { id: 'all', label: 'All', statuses: [] },
  {
    id: 'active',
    label: 'On the way',
    statuses: [
      'CREATED',
      'PICKUP_ASSIGNED',
      'PICKED_UP',
      'IN_TRANSIT',
      'OUT_FOR_DELIVERY',
      'DELIVERY_ATTEMPTED',
      'RETURNING'
    ]
  },
  { id: 'delivered', label: 'Delivered', statuses: ['DELIVERED'] },
  {
    id: 'closed',
    label: 'Cancelled or failed',
    statuses: ['CANCELLED', 'FAILED', 'RETURNED_TO_SENDER']
  }
];

export function inStatusGroup(order: CustomerOrderResponse, group: StatusGroup) {
  const def = STATUS_GROUPS.find((item) => item.id === group);
  return !def || def.statuses.length === 0 || def.statuses.includes(orderStatus(order));
}

/**
 * `StatusProgress` shows the five public milestones. Pickup assigned has no step of its own (it sits
 * between Created and Picked up), a failed attempt stays at Out for delivery, and a return and the
 * terminal failure states are shown with an alert instead.
 */
export function progressStep(status: PublicOrderStatus): StatusProgressStatus | null {
  switch (status) {
    case 'CREATED':
    case 'PICKUP_ASSIGNED':
      return 'CREATED';
    case 'PICKED_UP':
    case 'IN_TRANSIT':
    case 'OUT_FOR_DELIVERY':
    case 'DELIVERED':
      return status;
    case 'DELIVERY_ATTEMPTED':
      return 'OUT_FOR_DELIVERY';
    default:
      return null;
  }
}

const dateFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric'
});
const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit'
});

export function formatDate(value: string | undefined, withTime = false) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return (withTime ? dateTimeFormat : dateFormat).format(date);
}

/** `+94771234567` → `+94 77 123 4567`; anything else is shown as stored. */
export function formatPhone(value: string | undefined) {
  const match = /^\+94(\d{2})(\d{3})(\d{4})$/.exec(value ?? '');
  return match ? `+94 ${match[1]} ${match[2]} ${match[3]}` : (value ?? '');
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isUuid = (value: string | undefined): value is string =>
  Boolean(value && UUID.test(value));
