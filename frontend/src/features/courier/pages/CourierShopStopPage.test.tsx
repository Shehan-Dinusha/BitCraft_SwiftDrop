import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { deliveryApi, type ShopStopResponse } from '@/services/api/deliveryApi';
import { CourierShopStopPage } from './CourierShopStopPage';

const parcel = (id: string, fee: number, viaHub = false) => ({
  deliveryId: id,
  trackingNumber: `SD-${id}`,
  deliveryDistrict: viaHub ? ('KANDY' as const) : ('COLOMBO' as const),
  viaHub,
  fee
});

const stop: ShopStopResponse = {
  merchantId: 'm1',
  merchantName: 'Nimal Fashion',
  currency: 'LKR',
  addresses: [
    {
      pickup: { name: 'Nimal', phone: '+94771234567', addressLine1: '12 Galle Road' },
      parcels: [parcel('A', 350.1), parcel('B', 550.2, true), parcel('C', 550)],
      total: 1450.3
    }
  ]
};

const open = () =>
  render(
    <MemoryRouter initialEntries={['/courier/shops/m1']}>
      <Routes>
        <Route path="/courier/shops/:merchantId" element={<CourierShopStopPage />} />
      </Routes>
    </MemoryRouter>
  );

const cashBox = () => screen.getByRole('checkbox', { name: 'I have this cash from the shop' });
const pickUp = () => screen.getByRole('button', { name: /^Pick up/ });

describe('CourierShopStopPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(deliveryApi, 'getShopStop').mockResolvedValue(stop);
  });

  it('collects the ticked parcels with one exact amount and leaves the rest', async () => {
    const collect = vi.spyOn(deliveryApi, 'collectShop').mockResolvedValue({
      parcels: 2,
      payment: {
        paymentId: 'p1',
        district: 'COLOMBO',
        amount: 900.3,
        currency: 'LKR',
        parcels: 2,
        paidAt: '2026-11-22T10:00:00Z'
      }
    });
    open();

    expect(await screen.findByText('1,450.30')).toBeInTheDocument();
    expect(pickUp()).toBeDisabled();

    fireEvent.click(screen.getByRole('checkbox', { name: /SD-C/ }));
    expect(screen.getByText('900.30')).toBeInTheDocument();
    fireEvent.click(cashBox());
    fireEvent.click(pickUp());

    await waitFor(() =>
      expect(collect).toHaveBeenCalledWith('m1', { deliveryIds: ['A', 'B'], cashCollected: 900.3 })
    );
    expect(await screen.findByText('2 parcels picked up, LKR 900.30 collected')).toBeInTheDocument();
  });

  it('asks for the cash again when the parcels change', async () => {
    open();
    await screen.findByText('1,450.30');
    fireEvent.click(cashBox());
    expect(pickUp()).toBeEnabled();

    fireEvent.click(screen.getByRole('checkbox', { name: /SD-A/ }));

    expect(cashBox()).not.toBeChecked();
    expect(pickUp()).toBeDisabled();
  });

  it('cannot pick up nothing', async () => {
    open();
    await screen.findByText('1,450.30');
    for (const id of ['A', 'B', 'C']) {
      fireEvent.click(screen.getByRole('checkbox', { name: new RegExp(`SD-${id}`) }));
    }
    expect(pickUp()).toBeDisabled();
  });

  it('says why a collection was refused and loads the stop again', async () => {
    vi.spyOn(deliveryApi, 'collectShop').mockRejectedValue(
      new Error('SD-A was cancelled. Leave it at the shop and refresh.')
    );
    open();
    await screen.findByText('1,450.30');
    fireEvent.click(cashBox());
    fireEvent.click(pickUp());

    expect(await screen.findByRole('alert')).toHaveTextContent('SD-A was cancelled');
    await waitFor(() => expect(deliveryApi.getShopStop).toHaveBeenCalledTimes(2));
  });
});
