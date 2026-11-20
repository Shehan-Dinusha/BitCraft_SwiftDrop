import {
  Bell,
  ClipboardCheck,
  OctagonAlert,
  PackageCheck,
  PackageOpen,
  PackagePlus,
  PackageX,
  Siren,
  TriangleAlert,
  Truck,
  UserCheck,
  type LucideIcon
} from 'lucide-react';
import type { PersonaRole, PublicOrderStatus } from '@/types/contracts';
import { publicStatusColors } from '@/design-system/tokens/colors';

/** Notification types sent by notification-service (the type stored with each inbox row). */
export type NotificationType =
  | 'PARCEL_CREATED'
  | 'PARCEL_PICKED_UP'
  | 'PARCEL_OUT_FOR_DELIVERY'
  | 'PARCEL_DELIVERED'
  | 'PARCEL_FAILED'
  | 'COURIER_ASSIGNED'
  | 'MERCHANT_COURIER_ASSIGNED'
  | 'MERCHANT_DELIVERY_FAILED'
  | 'OPS_DELIVERY_FAILED'
  | 'DISPATCH_FAILED';

export interface NotificationTypeMeta {
  /** Short name used as the notification heading and in settings. */
  label: string;
  /** One-line description for the settings page. */
  description: string;
  Icon: LucideIcon;
  /** Delivery status whose SwiftDrop status colours tint the icon. */
  status: PublicOrderStatus;
  /** Whether this type is also emailed (team and operations alerts are in-app only). */
  emailed: boolean;
}

export const NOTIFICATION_TYPES: Record<NotificationType, NotificationTypeMeta> = {
  PARCEL_CREATED: {
    label: 'Order confirmed',
    description: 'When we receive your booking',
    Icon: ClipboardCheck,
    status: 'CREATED',
    emailed: true
  },
  PARCEL_PICKED_UP: {
    label: 'Picked up',
    description: 'When a courier collects your parcel',
    Icon: PackageOpen,
    status: 'PICKED_UP',
    emailed: true
  },
  PARCEL_OUT_FOR_DELIVERY: {
    label: 'Out for delivery',
    description: 'When your parcel is on the way to the recipient',
    Icon: Truck,
    status: 'OUT_FOR_DELIVERY',
    emailed: true
  },
  PARCEL_DELIVERED: {
    label: 'Delivered',
    description: 'When your parcel has been delivered',
    Icon: PackageCheck,
    status: 'DELIVERED',
    emailed: true
  },
  PARCEL_FAILED: {
    label: 'Delivery issue',
    description: 'When a delivery attempt does not succeed',
    Icon: TriangleAlert,
    status: 'FAILED',
    emailed: true
  },
  COURIER_ASSIGNED: {
    label: 'New delivery',
    description: 'When a parcel is assigned to you',
    Icon: PackagePlus,
    status: 'PICKUP_ASSIGNED',
    emailed: true
  },
  MERCHANT_COURIER_ASSIGNED: {
    label: 'Courier assigned',
    description: 'When a courier is on the way to collect one of your parcels',
    Icon: UserCheck,
    status: 'PICKUP_ASSIGNED',
    emailed: false
  },
  MERCHANT_DELIVERY_FAILED: {
    label: 'Delivery failed',
    description: 'When one of your parcels could not be delivered',
    Icon: PackageX,
    status: 'FAILED',
    emailed: false
  },
  OPS_DELIVERY_FAILED: {
    label: 'Delivery failed',
    description: 'When a parcel could not be delivered and needs a new attempt or a return',
    Icon: OctagonAlert,
    status: 'FAILED',
    emailed: false
  },
  DISPATCH_FAILED: {
    label: 'Dispatch needs attention',
    description: 'When automatic dispatch gives up on an order',
    Icon: Siren,
    status: 'FAILED',
    emailed: false
  }
};

/** Which notifications each workspace receives, in the order shown in settings. */
export const NOTIFICATION_TYPES_BY_ROLE: Record<PersonaRole, NotificationType[]> = {
  CUSTOMER: ['PARCEL_CREATED', 'PARCEL_PICKED_UP', 'PARCEL_OUT_FOR_DELIVERY', 'PARCEL_DELIVERED', 'PARCEL_FAILED'],
  COURIER: ['COURIER_ASSIGNED'],
  MERCHANT: ['MERCHANT_COURIER_ASSIGNED', 'MERCHANT_DELIVERY_FAILED'],
  ADMIN_OPS: ['OPS_DELIVERY_FAILED', 'DISPATCH_FAILED']
};

const FALLBACK: NotificationTypeMeta = {
  label: 'Update',
  description: 'Other SwiftDrop updates',
  Icon: Bell,
  status: 'CANCELLED',
  emailed: false
};

function isNotificationType(value: string | null | undefined): value is NotificationType {
  return !!value && value in NOTIFICATION_TYPES;
}

/**
 * Type for an inbox row. Newer rows carry `notificationType`; older rows are matched by title.
 */
export function resolveNotificationType(type?: string | null, title?: string | null): NotificationType | null {
  if (isNotificationType(type)) return type;
  const text = (title ?? '').toLowerCase();
  if (text.includes('order confirmed')) return 'PARCEL_CREATED';
  if (text.includes('picked up')) return 'PARCEL_PICKED_UP';
  if (text.includes('out for delivery')) return 'PARCEL_OUT_FOR_DELIVERY';
  if (text.includes('delivered')) return 'PARCEL_DELIVERED';
  if (text.includes('exception') || text.includes('fail') || text.includes('could not deliver')) return 'PARCEL_FAILED';
  return null;
}

export function notificationMeta(type: NotificationType | null): NotificationTypeMeta {
  return type ? NOTIFICATION_TYPES[type] : FALLBACK;
}

/** Tailwind classes for the round icon badge of a type, from the shared delivery-status tokens. */
export function notificationTone(type: NotificationType | null): string {
  const tone = publicStatusColors[notificationMeta(type).status];
  return `${tone.background} ${tone.foreground}`;
}

/** Where clicking a notification takes each role. */
export function notificationTarget(
  role: PersonaRole,
  type: NotificationType | null,
  trackingNumber?: string | null
): string | null {
  switch (role) {
    case 'CUSTOMER':
      return trackingNumber ? `/customer/tracking/${trackingNumber}` : null;
    case 'COURIER':
      return type === 'COURIER_ASSIGNED' ? '/courier' : null;
    case 'MERCHANT':
      return '/merchant';
    case 'ADMIN_OPS':
      return type === 'DISPATCH_FAILED' || type === 'OPS_DELIVERY_FAILED'
        ? '/ops/dispatch'
        : trackingNumber
          ? `/track/${trackingNumber}`
          : null;
    default:
      return null;
  }
}

/** Base path of each workspace, for the Notifications page route. */
export const NOTIFICATIONS_PATH: Record<PersonaRole, string> = {
  CUSTOMER: '/customer/notifications',
  COURIER: '/courier/notifications',
  MERCHANT: '/merchant/notifications',
  // `/ops/notifications` is the admin email-log console, so the operations inbox lives at /ops/inbox.
  ADMIN_OPS: '/ops/inbox'
};
