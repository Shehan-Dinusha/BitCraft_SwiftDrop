import type { CourierShiftResponse, DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import type { Speed } from '@/design-system';
import { districtLabel } from '@/features/profile/profileFormat';

/** Same day must be done today, Express by the next working day, Standard after. */
export const SPEED_RANK: Record<string, number> = { SAME_DAY: 0, EXPRESS: 1, STANDARD: 2 };

/** Most urgent speed first; the order is otherwise kept. */
export const bySpeed = <T extends { orderSpeed?: string | null }>(orders: T[]) =>
  [...orders].sort(
    (a, b) => (SPEED_RANK[a.orderSpeed ?? ''] ?? 2) - (SPEED_RANK[b.orderSpeed ?? ''] ?? 2)
  );
const SHIFT_RANK: Record<string, number> = { AVAILABLE: 0, BUSY: 1, ON_BREAK: 2, OFFLINE: 3 };

const time = (value?: string) => Date.parse(value ?? '') || 0;

/** Stable row key: one dispatch record per order. */
export const orderKey = (order: DispatchAssignmentResponse) =>
  order.orderId ?? order.assignmentId ?? order.trackingNumber ?? '';

export const orderLabel = (order: DispatchAssignmentResponse) =>
  order.trackingNumber ?? order.orderId ?? 'Order';

export function toSpeed(value?: string): Speed {
  if (value === 'EXPRESS') return 'express';
  return value === 'SAME_DAY' ? 'same-day' : 'standard';
}

export const isReturn = (order: DispatchAssignmentResponse) => order.leg === 'RETURN';

/** Waiting at this district's hub: to go out for delivery, or back to the sender. */
export const isAtHub = (order: DispatchAssignmentResponse) =>
  order.leg === 'LAST_MILE' || isReturn(order);

/** When the order joined its current queue: accepted at the hub, booked otherwise. */
export const waitingSince = (order: DispatchAssignmentResponse) =>
  isAtHub(order) ? (order.updatedAt ?? order.createdAt) : order.createdAt;

/** Most urgent speed first, then the order that has waited longest. */
export function sortQueue(orders: DispatchAssignmentResponse[]) {
  return [...orders].sort(
    (a, b) =>
      (SPEED_RANK[a.orderSpeed ?? ''] ?? 2) - (SPEED_RANK[b.orderSpeed ?? ''] ?? 2) ||
      time(waitingSince(a)) - time(waitingSince(b))
  );
}

/** Couriers who can work first, then by how full they are. */
export function sortCouriers(couriers: CourierShiftResponse[]) {
  const load = (c: CourierShiftResponse) => (c.activeParcelsCount ?? 0) / (c.maxCapacity || 1);
  return [...couriers].sort(
    (a, b) =>
      (SHIFT_RANK[a.shiftStatus ?? ''] ?? 4) - (SHIFT_RANK[b.shiftStatus ?? ''] ?? 4) ||
      load(a) - load(b) ||
      (a.courierName ?? '').localeCompare(b.courierName ?? '')
  );
}

/**
 * Orders that currently hold a courier are ASSIGNED or, after a reassignment, REASSIGNED. The endpoint
 * filters by one status at a time, so the pages are merged: one entry per order, newest first.
 */
export function mergeActiveAssignments(...pages: DispatchAssignmentResponse[][]) {
  const byKey = new Map<string, DispatchAssignmentResponse>();
  for (const order of pages.flat()) {
    const key = orderKey(order);
    if (key && !byKey.has(key)) byKey.set(key, order);
  }
  return [...byKey.values()].sort((a, b) => time(b.createdAt) - time(a.createdAt));
}

export interface DistrictGroup {
  district: string;
  label: string;
  orders: DispatchAssignmentResponse[];
}

/** Groups by where the parcels go (or come from), the biggest group first. */
export function groupByDistrict(
  orders: DispatchAssignmentResponse[],
  field: 'pickupDistrict' | 'deliveryDistrict'
): DistrictGroup[] {
  const groups = new Map<string, DispatchAssignmentResponse[]>();
  for (const order of orders) {
    const district = order[field] ?? '';
    groups.set(district, [...(groups.get(district) ?? []), order]);
  }
  return [...groups.entries()]
    .map(([district, list]) => ({
      district,
      label: districtLabel(district) || 'Unknown district',
      // Same day and Express go first at every hub step.
      orders: bySpeed(list)
    }))
    .sort((a, b) => b.orders.length - a.orders.length || a.label.localeCompare(b.label));
}

export interface ShopGroup {
  key: string;
  merchantId: string;
  name: string;
  district: string;
  /** Packed, longest waiting first. */
  ready: DispatchAssignmentResponse[];
  notReady: number;
  weightKg: number;
  readySince?: string;
}

/** Shops with parcels to collect, one entry per shop and district; those with packed parcels first. */
export function groupByShop(orders: DispatchAssignmentResponse[]): ShopGroup[] {
  const groups = new Map<string, ShopGroup>();
  for (const order of orders) {
    if (!order.shopPickup || !order.merchantId) continue;
    const district = order.pickupDistrict ?? '';
    const key = `${order.merchantId}:${district}`;
    const group = groups.get(key) ?? {
      key,
      merchantId: order.merchantId,
      name: 'Shop',
      district,
      ready: [],
      notReady: 0,
      weightKg: 0
    };
    if (order.merchantName) group.name = order.merchantName;
    if (order.readyForPickup) {
      group.ready.push(order);
      group.weightKg += order.weightKg ?? 0;
      const since = order.readyAt ?? order.createdAt;
      if (since && (!group.readySince || time(since) < time(group.readySince))) {
        group.readySince = since;
      }
    } else {
      group.notReady += 1;
    }
    groups.set(key, group);
  }
  return [...groups.values()].sort(
    (a, b) =>
      Number(b.ready.length > 0) - Number(a.ready.length > 0) ||
      time(a.readySince) - time(b.readySince) ||
      a.name.localeCompare(b.name)
  );
}

const contains = (values: (string | undefined)[], query: string) => {
  const term = query.trim().toLowerCase();
  return !term || values.some((value) => value?.toLowerCase().includes(term));
};

export const matchesOrder = (order: DispatchAssignmentResponse, query: string) =>
  contains(
    [
      order.trackingNumber,
      order.merchantName ?? undefined,
      order.courierName,
      districtLabel(order.pickupDistrict),
      districtLabel(order.deliveryDistrict),
      order.orderSpeed?.replace('_', ' ')
    ],
    query
  );

export const matchesCourier = (courier: CourierShiftResponse, query: string) =>
  contains(
    [
      courier.courierName,
      courier.phone,
      courier.vehicleType?.replace('_', ' '),
      districtLabel(courier.currentDistrict)
    ],
    query
  );

/** `5 min`, `2 h 10 min`, `3 d`. Empty when the time is unknown. */
export function formatAge(since: string | undefined, now: number): string {
  const start = time(since);
  if (!start) return '';
  const minutes = Math.max(0, Math.floor((now - start) / 60_000));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return minutes % 60 ? `${hours} h ${minutes % 60} min` : `${hours} h`;
  return `${Math.floor(hours / 24)} d`;
}
