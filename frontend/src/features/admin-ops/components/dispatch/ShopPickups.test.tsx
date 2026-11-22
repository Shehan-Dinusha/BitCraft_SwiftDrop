import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dispatchApi, type DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { groupByShop } from './dispatchQueues';
import { ShopPickups } from './ShopPickups';

const order = (
  id: string,
  over: Partial<DispatchAssignmentResponse> = {}
): DispatchAssignmentResponse => ({
  orderId: id,
  merchantId: 'm1',
  merchantName: 'Nimal Fashion',
  pickupDistrict: 'COLOMBO',
  shopPickup: true,
  readyForPickup: true,
  readyAt: '2026-11-22T10:00:00Z',
  weightKg: 1,
  ...over
});

describe('groupByShop', () => {
  it('makes one row per shop, and keeps parcels that are not packed apart', () => {
    const shops = groupByShop([
      order('1'),
      order('2', { readyAt: '2026-11-22T09:00:00Z', weightKg: 2 }),
      order('3', { readyForPickup: false, readyAt: undefined }),
      order('4', { merchantId: 'm2', merchantName: null, readyForPickup: false }),
      order('5', { shopPickup: false })
    ]);

    expect(shops.map((shop) => [shop.name, shop.ready.length, shop.notReady])).toEqual([
      ['Nimal Fashion', 2, 1],
      ['Shop', 0, 1]
    ]);
    expect(shops[0].weightKg).toBe(3);
    expect(shops[0].readySince).toBe('2026-11-22T09:00:00Z');
  });
});

describe('ShopPickups', () => {
  const onAssigned = vi.fn();
  const show = (orders: DispatchAssignmentResponse[]) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ShopPickups
          shops={groupByShop(orders)}
          now={Date.parse('2026-11-22T10:30:00Z')}
          showDistrict={false}
          onAssigned={onAssigned}
        />
      </QueryClientProvider>
    );

  beforeEach(() => {
    vi.restoreAllMocks();
    onAssigned.mockReset();
  });

  it('gives the courier exactly the parcels that fit, and says what is left', async () => {
    vi.spyOn(dispatchApi, 'getShopCouriers').mockResolvedValue([
      {
        courierId: 'c1',
        courierName: 'Nuwan',
        vehicleType: 'MOTORCYCLE',
        activeParcels: 4,
        maxCapacity: 5,
        fittingOrderIds: ['1']
      },
      {
        courierId: 'c2',
        courierName: 'Sunil',
        vehicleType: 'MOTORCYCLE',
        activeParcels: 5,
        maxCapacity: 5,
        fittingOrderIds: []
      }
    ]);
    const assign = vi.spyOn(dispatchApi, 'assignShop').mockResolvedValue([order('1')]);
    show([order('1'), order('2')]);

    fireEvent.click(screen.getByRole('button', { name: 'Assign Nimal Fashion' }));
    expect(await screen.findByText('Takes 1, leaves 1')).toBeInTheDocument();
    expect(screen.queryByText('Sunil')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Assign 1 parcel' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('m1', 'c1', ['1']));
    expect(onAssigned).toHaveBeenCalledWith('1 parcel of Nimal Fashion assigned to Nuwan');
  });

  it('cannot send a courier to a shop that has not packed yet', () => {
    show([order('1', { readyForPickup: false, readyAt: undefined })]);

    expect(screen.getByRole('button', { name: 'Assign Nimal Fashion' })).toBeDisabled();
    expect(screen.getByText('1 not ready')).toBeInTheDocument();
  });
});
