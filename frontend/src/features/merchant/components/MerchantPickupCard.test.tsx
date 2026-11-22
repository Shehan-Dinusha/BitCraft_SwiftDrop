import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { merchantApi, type MerchantPickupResponse } from '@/services/api/merchantApi';
import { orderApi } from '@/services/api/orderApi';
import { MerchantPickupCard } from './MerchantPickupCard';

const group = (parcels: number, total: number, withoutFee = 0) => ({ parcels, total, withoutFee });

const pickup: MerchantPickupResponse = {
  waiting: group(1, 350),
  ready: group(1, 550),
  assigned: group(1, 350, 1),
  currency: 'LKR',
  parcels: [
    {
      orderId: 'o1',
      trackingNumber: 'SD-1',
      externalRef: 'SHOP-1',
      state: 'WAITING',
      fee: 350
    },
    {
      orderId: 'o2',
      trackingNumber: 'SD-2',
      externalRef: 'SHOP-2',
      state: 'ASSIGNED',
      courierName: 'Nuwan',
      fee: 350
    }
  ]
};

describe('MerchantPickupCard', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(merchantApi, 'getPickup').mockResolvedValue(pickup);
  });

  it('shows the cash to prepare for parcels packed or about to be collected', async () => {
    render(<MerchantPickupCard merchantId="m1" />);

    expect(await screen.findByText('900.00')).toBeInTheDocument();
    expect(screen.getByText('plus 1 older parcel')).toBeInTheDocument();
    expect(screen.getByText('Nuwan is coming')).toBeInTheDocument();
  });

  it('marks everything ready, and offers nothing more until a new order waits', async () => {
    const ready = vi
      .spyOn(merchantApi, 'markPickupReady')
      .mockResolvedValue({ ...pickup, waiting: group(0, 0), ready: group(2, 900) });
    render(<MerchantPickupCard merchantId="m1" />);

    fireEvent.click(await screen.findByRole('button', { name: 'Ready for pickup' }));

    await waitFor(() => expect(ready).toHaveBeenCalledWith('m1'));
    expect(await screen.findByText('Marked ready. A courier will be sent.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ready for pickup' })).toBeDisabled();
  });

  it('prints the labels of every parcel at the shop', async () => {
    const labels = vi.spyOn(orderApi, 'downloadShippingLabels').mockResolvedValue();
    render(<MerchantPickupCard merchantId="m1" />);

    const print = await screen.findByRole('button', { name: 'Print labels' });
    await waitFor(() => expect(print).toBeEnabled());
    fireEvent.click(print);

    await waitFor(() => expect(labels).toHaveBeenCalledWith(['o1', 'o2']));
  });
});
