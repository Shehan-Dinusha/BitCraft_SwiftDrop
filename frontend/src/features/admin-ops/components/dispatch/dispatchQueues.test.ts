import { describe, expect, it } from 'vitest';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import {
  formatAge,
  groupByDistrict,
  matchesOrder,
  mergeActiveAssignments,
  sortCouriers,
  sortQueue
} from './dispatchQueues';

const order = (over: DispatchAssignmentResponse): DispatchAssignmentResponse => ({
  orderId: over.trackingNumber,
  orderSpeed: 'STANDARD',
  createdAt: '2026-11-22T08:00:00Z',
  ...over
});
const ids = (orders: DispatchAssignmentResponse[]) => orders.map((o) => o.trackingNumber);

describe('sortQueue', () => {
  it('puts same day first, then express, then the longest wait', () => {
    expect(
      ids(
        sortQueue([
          order({ trackingNumber: 'new-standard', createdAt: '2026-11-22T10:00:00Z' }),
          order({ trackingNumber: 'old-standard', createdAt: '2026-11-22T07:00:00Z' }),
          order({ trackingNumber: 'same-day', orderSpeed: 'SAME_DAY' }),
          order({
            trackingNumber: 'express',
            orderSpeed: 'EXPRESS',
            createdAt: '2026-11-22T11:00:00Z'
          })
        ])
      )
    ).toEqual(['same-day', 'express', 'old-standard', 'new-standard']);
  });

  it('counts a hub parcel from when the hub accepted it, not from booking', () => {
    const atHub = order({
      trackingNumber: 'hub',
      leg: 'LAST_MILE',
      createdAt: '2026-11-20T08:00:00Z',
      updatedAt: '2026-11-22T09:00:00Z'
    });
    expect(ids(sortQueue([atHub, order({ trackingNumber: 'pickup' })]))).toEqual(['pickup', 'hub']);
  });
});

describe('grouping and merging', () => {
  it('groups by destination with the biggest group first', () => {
    const groups = groupByDistrict(
      [
        order({ trackingNumber: 'a', deliveryDistrict: 'GALLE' }),
        order({ trackingNumber: 'b', deliveryDistrict: 'NUWARA_ELIYA' }),
        order({ trackingNumber: 'c', deliveryDistrict: 'NUWARA_ELIYA' })
      ],
      'deliveryDistrict'
    );
    expect(groups.map((g) => [g.label, g.orders.length])).toEqual([
      ['Nuwara Eliya', 2],
      ['Galle', 1]
    ]);
  });

  it('keeps one entry per order, newest first', () => {
    const merged = mergeActiveAssignments(
      [order({ trackingNumber: 'a' })],
      [
        order({ trackingNumber: 'a' }),
        order({ trackingNumber: 'b', createdAt: '2026-11-22T09:00:00Z' })
      ]
    );
    expect(ids(merged)).toEqual(['b', 'a']);
  });
});

it('searches districts by the name people see', () => {
  const target = order({ trackingNumber: 'SD-1', deliveryDistrict: 'NUWARA_ELIYA' });
  expect(matchesOrder(target, 'nuwara eliya')).toBe(true);
  expect(matchesOrder(target, 'sd-1')).toBe(true);
  expect(matchesOrder(target, 'galle')).toBe(false);
});

it('lists working couriers before offline ones, the emptiest first', () => {
  const names = sortCouriers([
    { courierName: 'Off', shiftStatus: 'OFFLINE', activeParcelsCount: 0, maxCapacity: 5 },
    { courierName: 'Half', shiftStatus: 'AVAILABLE', activeParcelsCount: 5, maxCapacity: 10 },
    { courierName: 'Light', shiftStatus: 'AVAILABLE', activeParcelsCount: 1, maxCapacity: 25 }
  ]).map((c) => c.courierName);
  expect(names).toEqual(['Light', 'Half', 'Off']);
});

it.each([
  ['2026-11-22T09:55:00Z', '5 min'],
  ['2026-11-22T07:50:00Z', '2 h 10 min'],
  ['2026-11-22T08:00:00Z', '2 h'],
  ['2026-11-19T09:00:00Z', '3 d'],
  [undefined, '']
])('formats the wait from %s as "%s"', (since, expected) => {
  expect(formatAge(since, Date.parse('2026-11-22T10:00:00Z'))).toBe(expected);
});
