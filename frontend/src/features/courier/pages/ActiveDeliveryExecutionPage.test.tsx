import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  deliveryApi,
  type CourierJobResponse,
  type DeliverySessionResponse
} from '@/services/api/deliveryApi';
import { ActiveDeliveryExecutionPage } from './ActiveDeliveryExecutionPage';

const session: DeliverySessionResponse = {
  deliveryId: 'd1',
  orderId: 'o1',
  trackingNumber: 'SD-7K4M-9QX2',
  pickupDistrict: 'COLOMBO',
  deliveryDistrict: 'COLOMBO',
  currentState: 'ASSIGNED'
};

const job = (over: Partial<CourierJobResponse>): CourierJobResponse => ({
  deliveryId: 'd1',
  cancelled: false,
  currency: 'LKR',
  ...over
});

const open = () =>
  render(
    <MemoryRouter initialEntries={['/courier/delivery/d1']}>
      <Routes>
        <Route path="/courier/delivery/:id" element={<ActiveDeliveryExecutionPage />} />
      </Routes>
    </MemoryRouter>
  );

const pickupButtons = () => screen.queryAllByRole('button', { name: 'Confirm pickup' });

describe('ActiveDeliveryExecutionPage cash', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    // jsdom has none; the slide bar only measures itself with it.
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    vi.spyOn(deliveryApi, 'getMyDeliveries').mockResolvedValue([session]);
    vi.spyOn(deliveryApi, 'getDeliveryById').mockResolvedValue(session);
    vi.spyOn(deliveryApi, 'getProofOfDelivery').mockRejectedValue(new Error('none'));
  });

  it('keeps the pickup closed until the cash is ticked, then sends the exact amount', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockResolvedValue(job({ feeToCollect: 670 }));
    const transition = vi
      .spyOn(deliveryApi, 'transitionDelivery')
      .mockResolvedValue({ ...session, currentState: 'PICKED_UP' });
    open();

    const box = await screen.findByRole('checkbox');
    pickupButtons().forEach((button) => expect(button).toBeDisabled());

    fireEvent.click(box);
    await waitFor(() => expect(pickupButtons()[0]).toBeEnabled());
    fireEvent.click(pickupButtons()[0]);

    await waitFor(() =>
      expect(transition).toHaveBeenCalledWith(
        'd1',
        expect.objectContaining({ targetState: 'PICKED_UP', cashCollected: 670 })
      )
    );
  });

  it('needs no tick, and sends no amount, when nothing is due', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockResolvedValue(job({}));
    const transition = vi
      .spyOn(deliveryApi, 'transitionDelivery')
      .mockResolvedValue({ ...session, currentState: 'PICKED_UP' });
    open();

    await waitFor(() => expect(pickupButtons()[0]).toBeEnabled());
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    fireEvent.click(pickupButtons()[0]);

    await waitFor(() => expect(transition).toHaveBeenCalled());
    expect(transition.mock.calls[0][1].cashCollected).toBeUndefined();
  });

  it('waits when the amount is unknown because the job did not load', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockRejectedValue(new Error('down'));
    open();

    expect(await screen.findByText('Addresses not loaded')).toBeInTheDocument();
    pickupButtons().forEach((button) => expect(button).toBeDisabled());
  });

  it('offers no pickup for a cancelled order', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockResolvedValue(job({ cancelled: true }));
    open();

    expect(await screen.findByText('This order was cancelled')).toBeInTheDocument();
    expect(pickupButtons()).toHaveLength(0);
  });
});
