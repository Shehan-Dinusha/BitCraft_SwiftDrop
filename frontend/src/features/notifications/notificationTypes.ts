import {
  BadgeCheck,
  Bell,
  CalendarClock,
  ClipboardCheck,
  FileWarning,
  Store,
  OctagonAlert,
  PackageCheck,
  PackageMinus,
  PackageOpen,
  PackagePlus,
  PackageX,
  RotateCcw,
  Siren,
  TriangleAlert,
  Truck,
  Undo2,
  UserCheck,
  type LucideIcon
} from 'lucide-react';
import type { PersonaRole, PublicOrderStatus } from '@/types/contracts';
import { publicStatusColors } from '@/design-system/tokens/colors';

/** Notification types sent by notification-service (the type stored with each inbox row). */
export type NotificationType =
  | 'PARCEL_CREATED'
  | 'PARCEL_PICKUP_ASSIGNED'
  | 'PARCEL_PICKED_UP'
  | 'PARCEL_OUT_FOR_DELIVERY'
  | 'PARCEL_DELIVERED'
  | 'PARCEL_FAILED'
  | 'PARCEL_RETURNING'
  | 'PARCEL_RETURNED'
  | 'MERCHANT_RETURN'
  | 'COURIER_ASSIGNED'
  | 'MERCHANT_COURIER_ASSIGNED'
  | 'MERCHANT_DELIVERY_FAILED'
  | 'OPS_DELIVERY_FAILED'
  | 'DISPATCH_FAILED'
  | 'MERCHANT_APPLICATION'
  | 'MERCHANT_APPROVED'
  | 'MERCHANT_REJECTED';

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
  PARCEL_PICKUP_ASSIGNED: {
    label: 'Courier assigned',
    description: 'When a courier is coming to collect your parcel',
    Icon: CalendarClock,
    status: 'PICKUP_ASSIGNED',
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
  PARCEL_RETURNING: {
    label: 'Coming back',
    description: 'When a parcel that could not be delivered is on its way back to you',
    Icon: Undo2,
    status: 'RETURNING',
    emailed: true
  },
  PARCEL_RETURNED: {
    label: 'Returned',
    description: 'When a parcel has been handed back to you',
    Icon: PackageMinus,
    status: 'RETURNED_TO_SENDER',
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
  MERCHANT_RETURN: {
    label: 'Return',
    description: 'When one of your parcels comes back to you',
    Icon: RotateCcw,
    status: 'RETURNING',
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
  },
  MERCHANT_APPLICATION: {
    label: 'New merchant application',
    description: 'When a business in your district applies to ship with SwiftDrop',
    Icon: Store,
    status: 'PICKUP_ASSIGNED',
    emailed: false
  },
  MERCHANT_APPROVED: {
    label: 'Business approved',
    description: 'When your business application is approved',
    Icon: BadgeCheck,
    status: 'DELIVERED',
    emailed: true
  },
  MERCHANT_REJECTED: {
    label: 'Application needs changes',
    description: 'When your business application could not be approved',
    Icon: FileWarning,
    status: 'FAILED',
    emailed: true
  }
};

/** Which notifications each workspace receives, in the order shown in settings. */
export const NOTIFICATION_TYPES_BY_ROLE: Record<PersonaRole, NotificationType[]> = {
  CUSTOMER: [
    'PARCEL_CREATED',
    'PARCEL_PICKUP_ASSIGNED',
    'PARCEL_PICKED_UP',
    'PARCEL_OUT_FOR_DELIVERY',
    'PARCEL_DELIVERED',
    'PARCEL_FAILED',
    'PARCEL_RETURNING',
    'PARCEL_RETURNED'
  ],
  COURIER: ['COURIER_ASSIGNED'],
  MERCHANT: [
    'MERCHANT_APPROVED',
    'MERCHANT_REJECTED',
    'MERCHANT_COURIER_ASSIGNED',
    'MERCHANT_DELIVERY_FAILED',
    'MERCHANT_RETURN'
  ],
  ADMIN_OPS: ['OPS_DELIVERY_FAILED', 'DISPATCH_FAILED', 'MERCHANT_APPLICATION']
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

/**
 * Where clicking a notification takes each role. A system admin (`backendRole`) has no dispatch or
 * merchant-application pages, so their notifications open the parcel's public tracking page.
 */
export function notificationTarget(
  role: PersonaRole,
  type: NotificationType | null,
  trackingNumber?: string | null,
  backendRole?: string | null
): string | null {
  switch (role) {
    case 'CUSTOMER':
      return trackingNumber ? `/customer/tracking/${trackingNumber}` : null;
    case 'COURIER':
      return type === 'COURIER_ASSIGNED' ? '/courier' : null;
    case 'MERCHANT':
      return '/merchant';
    case 'ADMIN_OPS':
      if (backendRole === 'SYSTEM_ADMIN') return trackingNumber ? `/track/${trackingNumber}` : null;
      if (type === 'MERCHANT_APPLICATION') return '/ops/merchants';
      return type === 'DISPATCH_FAILED' || type === 'OPS_DELIVERY_FAILED'
        ? type === 'OPS_DELIVERY_FAILED'
          ? '/ops/dispatch?tab=returns'
          : '/ops/dispatch'
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
