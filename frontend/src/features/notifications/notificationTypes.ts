import { Bell, ClipboardCheck, PackageCheck, PackageOpen, TriangleAlert, Truck, type LucideIcon } from 'lucide-react';
import type { PublicOrderStatus } from '@/types/contracts';
import { publicStatusColors } from '@/design-system/tokens/colors';

/** Notification types sent by notification-service (the template code stored with each inbox row). */
export type NotificationType =
  | 'PARCEL_CREATED'
  | 'PARCEL_PICKED_UP'
  | 'PARCEL_OUT_FOR_DELIVERY'
  | 'PARCEL_DELIVERED'
  | 'PARCEL_FAILED';

export interface NotificationTypeMeta {
  /** Short name used as the notification heading and in settings. */
  label: string;
  /** One-line description for the settings page. */
  description: string;
  Icon: LucideIcon;
  /** Delivery status whose Velocity colours tint the icon. */
  status: PublicOrderStatus;
}

export const NOTIFICATION_TYPES: Record<NotificationType, NotificationTypeMeta> = {
  PARCEL_CREATED: {
    label: 'Order confirmed',
    description: 'When we receive your booking',
    Icon: ClipboardCheck,
    status: 'CREATED'
  },
  PARCEL_PICKED_UP: {
    label: 'Picked up',
    description: 'When a courier collects your parcel',
    Icon: PackageOpen,
    status: 'PICKED_UP'
  },
  PARCEL_OUT_FOR_DELIVERY: {
    label: 'Out for delivery',
    description: 'When your parcel is on the way to the recipient',
    Icon: Truck,
    status: 'OUT_FOR_DELIVERY'
  },
  PARCEL_DELIVERED: {
    label: 'Delivered',
    description: 'When your parcel has been delivered',
    Icon: PackageCheck,
    status: 'DELIVERED'
  },
  PARCEL_FAILED: {
    label: 'Delivery issue',
    description: 'When a delivery attempt does not succeed',
    Icon: TriangleAlert,
    status: 'FAILED'
  }
};

export const NOTIFICATION_TYPE_ORDER: NotificationType[] = [
  'PARCEL_CREATED',
  'PARCEL_PICKED_UP',
  'PARCEL_OUT_FOR_DELIVERY',
  'PARCEL_DELIVERED',
  'PARCEL_FAILED'
];

const FALLBACK: NotificationTypeMeta = {
  label: 'Update',
  description: 'Other SwiftDrop updates',
  Icon: Bell,
  status: 'CANCELLED'
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
  if (text.includes('exception') || text.includes('fail')) return 'PARCEL_FAILED';
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
