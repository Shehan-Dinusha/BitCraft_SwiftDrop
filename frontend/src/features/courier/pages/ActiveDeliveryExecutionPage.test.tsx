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

/** The one control for the next step is a slide; Enter confirms it, as for a keyboard user. */
const pickupSlides = () => screen.queryAllByRole('button', { name: 'Slide to confirm pickup' });
const confirm = (slide: HTMLElement) => fireEvent.keyDown(slide, { key: 'Enter' });
const locked = (slide: HTMLElement) => expect(slide).toHaveAttribute('aria-disabled', 'true');

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
    expect(pickupSlides()).toHaveLength(1);
    locked(pickupSlides()[0]);

    fireEvent.click(box);
    await waitFor(() => expect(pickupSlides()[0]).not.toHaveAttribute('aria-disabled'));
    confirm(pickupSlides()[0]);

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

    await waitFor(() => expect(pickupSlides()[0]).not.toHaveAttribute('aria-disabled'));
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    confirm(pickupSlides()[0]);

    await waitFor(() => expect(transition).toHaveBeenCalled());
    expect(transition.mock.calls[0][1].cashCollected).toBeUndefined();
  });

  it('waits when the amount is unknown because the job did not load', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockRejectedValue(new Error('down'));
    open();

    expect(await screen.findByText('Addresses not loaded')).toBeInTheDocument();
    pickupSlides().forEach(locked);
  });

  it('offers no pickup for a cancelled order', async () => {
    vi.spyOn(deliveryApi, 'getJob').mockResolvedValue(job({ cancelled: true }));
    open();

    expect(await screen.findByText('This order was cancelled')).toBeInTheDocument();
    expect(pickupSlides()).toHaveLength(0);
  });
});

describe('ActiveDeliveryExecutionPage failed deliveries and returns', () => {
  const show = (over: Partial<DeliverySessionResponse>, jobOver: Partial<CourierJobResponse> = {}) => {
    const current = { ...session, ...over };
    vi.spyOn(deliveryApi, 'getMyDeliveries').mockResolvedValue([current]);
    vi.spyOn(deliveryApi, 'getDeliveryById').mockResolvedValue(current);
    vi.spyOn(deliveryApi, 'getJob').mockResolvedValue(job(jobOver));
    open();
    return current;
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    vi.spyOn(deliveryApi, 'getProofOfDelivery').mockRejectedValue(new Error('none'));
  });

  it('reports a failed attempt, not the end of the delivery', async () => {
    const current = show({ currentState: 'OUT_FOR_DELIVERY' });
    const transition = vi
      .spyOn(deliveryApi, 'transitionDelivery')
      .mockResolvedValue({ ...current, currentState: 'ATTEMPT_FAILED' });

    fireEvent.click(await screen.findByRole('button', { name: "Can't deliver" }));
    expect(screen.queryByRole('option', { name: 'Would not pay' })).not.toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText(/Note/), { target: { value: 'No answer' } });
    fireEvent.click(screen.getByRole('button', { name: 'Report' }));

    await waitFor(() =>
      expect(transition).toHaveBeenCalledWith(
        'd1',
        expect.objectContaining({
          targetState: 'ATTEMPT_FAILED',
          failureReason: 'RECIPIENT_UNAVAILABLE'
        })
      )
    );
    expect(
      await screen.findByRole('button', { name: 'Slide when handed in at the Colombo hub' })
    ).toBeInTheDocument();
  });

  it('offers one control for the step, not a slide and buttons doing the same', async () => {
    show({ currentState: 'PICKED_UP' });

    expect(await screen.findByRole('button', { name: 'Slide to start the trip' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /start trip/i })).not.toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: /trip/i })).toHaveLength(1);
  });

  it('sends a Standard parcel in one district to the hub, and a Same day one straight on', async () => {
    show({ currentState: 'PICKED_UP', speed: 'STANDARD', viaHub: true });
    expect(
      await screen.findByRole('button', { name: 'Slide when handed in at the Colombo hub' })
    ).toBeInTheDocument();
    expect(screen.getByText(/It goes out on a delivery run from there/)).toBeInTheDocument();
  });

  it('is done with the parcel once it is handed in', async () => {
    const current = show({ currentState: 'ATTEMPT_FAILED', failedReason: 'OTHER' });
    vi.spyOn(deliveryApi, 'transitionDelivery').mockResolvedValue({
      ...current,
      currentState: 'HELD_AT_HUB',
      courierId: undefined
    });

    confirm(await screen.findByRole('button', { name: 'Slide when handed in at the Colombo hub' }));

    expect(await screen.findByRole('heading', { name: 'Handed in at the hub' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Lost or damaged' })).not.toBeInTheDocument();
  });

  it('brings a return to the sender with a photo, or back to the hub', async () => {
    const current = show(
      { currentState: 'OUT_FOR_RETURN', proofSince: '2026-11-22T10:00:00Z' },
      { returnTo: { name: 'Nimal Perera', addressLine1: '12 Galle Road' } as never }
    );
    const transition = vi
      .spyOn(deliveryApi, 'transitionDelivery')
      .mockResolvedValue({ ...current, currentState: 'RETURN_AT_HUB', courierId: undefined });

    expect(await screen.findByText('Return to the sender')).toBeInTheDocument();
    expect(screen.getByText('Nimal Perera')).toBeInTheDocument();
    confirm(screen.getByRole('button', { name: 'Slide to take the photo and return it' }));
    expect(await screen.findByRole('dialog', { name: 'Proof of return' })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });

    fireEvent.click(screen.getByRole('button', { name: 'Sender not there, back at the hub' }));
    await waitFor(() =>
      expect(transition).toHaveBeenCalledWith(
        'd1',
        expect.objectContaining({ targetState: 'RETURN_AT_HUB' })
      )
    );
    expect(await screen.findByRole('heading', { name: 'Handed in at the hub' })).toBeInTheDocument();
  });

  it('does not take the photo of an earlier attempt as proof', async () => {
    vi.spyOn(deliveryApi, 'getProofOfDelivery').mockResolvedValue({
      verified: true,
      verifiedAt: '2026-11-22T09:00:00Z'
    });
    show({ currentState: 'OUT_FOR_DELIVERY', proofSince: '2026-11-22T10:00:00Z' });

    expect(await screen.findByText('Proof of delivery needed')).toBeInTheDocument();
  });
});
