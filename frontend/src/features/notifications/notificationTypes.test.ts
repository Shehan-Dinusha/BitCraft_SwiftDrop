import { describe, expect, it } from 'vitest';
import {
  NOTIFICATIONS_PATH,
  NOTIFICATION_TYPES,
  NOTIFICATION_TYPES_BY_ROLE,
  notificationTarget
} from './notificationTypes';

describe('notification types per role', () => {
  it('gives every workspace its own notification types', () => {
    expect(NOTIFICATION_TYPES_BY_ROLE.CUSTOMER).toContain('PARCEL_DELIVERED');
    expect(NOTIFICATION_TYPES_BY_ROLE.COURIER).toEqual(['COURIER_ASSIGNED']);
    expect(NOTIFICATION_TYPES_BY_ROLE.MERCHANT).toEqual([
      'MERCHANT_APPROVED',
      'MERCHANT_REJECTED',
      'MERCHANT_COURIER_ASSIGNED',
      'MERCHANT_DELIVERY_FAILED',
      'MERCHANT_RETURN'
    ]);
    expect(NOTIFICATION_TYPES_BY_ROLE.ADMIN_OPS).toEqual(['OPS_DELIVERY_FAILED', 'DISPATCH_FAILED', 'MERCHANT_APPLICATION']);
  });

  it('uses a distinct icon for every type', () => {
    const icons = Object.values(NOTIFICATION_TYPES).map((t) => t.Icon);
    expect(new Set(icons).size).toBe(icons.length);
  });

  it('marks team alerts as in-app only', () => {
    expect(NOTIFICATION_TYPES.DISPATCH_FAILED.emailed).toBe(false);
    expect(NOTIFICATION_TYPES.MERCHANT_DELIVERY_FAILED.emailed).toBe(false);
    expect(NOTIFICATION_TYPES.OPS_DELIVERY_FAILED.emailed).toBe(false);
    expect(NOTIFICATION_TYPES.COURIER_ASSIGNED.emailed).toBe(true);
  });

  it('has a Notifications page per workspace that does not clash with the admin email console', () => {
    expect(NOTIFICATIONS_PATH).toEqual({
      CUSTOMER: '/customer/notifications',
      COURIER: '/courier/notifications',
      MERCHANT: '/merchant/notifications',
      ADMIN_OPS: '/ops/inbox'
    });
  });

  it('opens the right screen when a notification is clicked', () => {
    expect(notificationTarget('CUSTOMER', 'PARCEL_DELIVERED', 'SD-AAAA-BBBB')).toBe('/customer/tracking/SD-AAAA-BBBB');
    expect(notificationTarget('COURIER', 'COURIER_ASSIGNED', 'SD-AAAA-BBBB')).toBe('/courier');
    expect(notificationTarget('MERCHANT', 'MERCHANT_DELIVERY_FAILED', 'SD-AAAA-BBBB')).toBe('/merchant');
    expect(notificationTarget('ADMIN_OPS', 'OPS_DELIVERY_FAILED', 'SD-AAAA-BBBB')).toBe('/ops/dispatch?tab=returns');
    expect(notificationTarget('ADMIN_OPS', 'DISPATCH_FAILED', null)).toBe('/ops/dispatch');
    expect(notificationTarget('ADMIN_OPS', 'OPS_DELIVERY_FAILED', 'SD-AAAA-BBBB', 'SYSTEM_ADMIN')).toBe('/track/SD-AAAA-BBBB');
    expect(notificationTarget('ADMIN_OPS', 'MERCHANT_APPLICATION', null, 'SYSTEM_ADMIN')).toBeNull();
    expect(notificationTarget('CUSTOMER', 'PARCEL_DELIVERED', null)).toBeNull();
  });
});
