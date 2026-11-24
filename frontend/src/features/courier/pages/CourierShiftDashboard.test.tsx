import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/store/useAuthStore';
import { dispatchApi, type CourierShiftResponse } from '@/services/api/dispatchApi';
import { deliveryApi, type DeliverySessionResponse } from '@/services/api/deliveryApi';
import { cashApi } from '@/services/api/cashApi';
import { CourierShiftDashboard } from './CourierShiftDashboard';

const shift: CourierShiftResponse = {
  courierId: 'c1',
  courierName: 'Nuwan',
  shiftStatus: 'AVAILABLE',
  activeParcelsCount: 3,
  maxCapacity: 5,
  startedLate: false,
  closedAutomatically: false
};

const job = (id: string, currentState: DeliverySessionResponse['currentState']): DeliverySessionResponse => ({
  deliveryId: id,
  trackingNumber: `SD-${id}`,
  pickupDistrict: 'COLOMBO',
  deliveryDistrict: 'KANDY',
  currentState
});

const open = () =>
  render(
    <MemoryRouter>
      <CourierShiftDashboard />
    </MemoryRouter>
  );

describe('CourierShiftDashboard: the morning at the hub', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.setState({ user: { id: 'c1', role: 'COURIER', district: 'KANDY' } });
    vi.spyOn(dispatchApi, 'getMyProfile').mockResolvedValue({ vehicleType: 'MOTORCYCLE' });
    vi.spyOn(dispatchApi, 'getCourier').mockResolvedValue(shift);
    vi.spyOn(dispatchApi, 'getWorkday').mockRejectedValue(new Error('offline'));
    vi.spyOn(cashApi, 'mine').mockResolvedValue({ inHand: 0 } as never);
  });

  it('loads the ticked hub parcels in one go, and leaves only once nothing is left to load', async () => {
    const jobs = [job('A', 'AT_DESTINATION_HUB'), job('B', 'RETURN_AT_HUB'), job('C', 'ASSIGNED')];
    vi.spyOn(deliveryApi, 'getMyDeliveries').mockResolvedValue(jobs);
    const load = vi.spyOn(deliveryApi, 'loadAtHub').mockResolvedValue([job('A', 'OUT_FOR_DELIVERY')]);
    open();

    const start = await screen.findByRole('button', { name: 'Start run' });
    expect(start).toBeDisabled();
    expect(screen.getByRole('region', { name: 'Pickups' })).toHaveTextContent('SD-C');

    fireEvent.click(screen.getByRole('checkbox', { name: /SD-B/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Load 1 parcel' }));
    await waitFor(() => expect(load).toHaveBeenCalledWith(['A']));
  });

  it('starts the run when the hub has nothing left for the courier', async () => {
    vi.spyOn(deliveryApi, 'getMyDeliveries').mockResolvedValue([job('C', 'ASSIGNED')]);
    const run = vi
      .spyOn(dispatchApi, 'startRun')
      .mockResolvedValue({ ...shift, runStartedAt: '2026-11-24T03:40:00Z' });
    open();

    expect(await screen.findByText('Nothing waiting for you at the hub.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start run' }));
    await waitFor(() => expect(run).toHaveBeenCalledWith('c1'));
    expect(await screen.findByText(/On the road since 09:10/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start run' })).not.toBeInTheDocument();
  });

  it('comes back to the hub and hands in: pickups ticked, a parcel not delivered offered unticked', async () => {
    vi.spyOn(dispatchApi, 'getCourier').mockResolvedValue({
      ...shift,
      runStartedAt: '2026-11-24T03:40:00Z'
    });
    const jobs = [
      { ...job('P', 'PICKED_UP'), speed: 'STANDARD', viaHub: true },
      job('F', 'ATTEMPT_FAILED'),
      { ...job('D', 'OUT_FOR_DELIVERY'), viaHub: true }
    ] as DeliverySessionResponse[];
    vi.spyOn(deliveryApi, 'getMyDeliveries').mockResolvedValue(jobs);
    const back = vi
      .spyOn(dispatchApi, 'backAtHub')
      .mockResolvedValue({ ...shift, lastRunEndedAt: '2026-11-24T11:00:00Z' });
    const handIn = vi.spyOn(deliveryApi, 'handIn').mockResolvedValue([]);
    open();

    expect(await screen.findByRole('button', { name: 'End shift' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Back at the hub' }));
    await waitFor(() => expect(back).toHaveBeenCalledWith('c1'));

    expect(await screen.findByRole('checkbox', { name: /SD-D/ })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: /SD-P/ })).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Hand in 2 parcels' }));
    await waitFor(() => expect(handIn).toHaveBeenCalledWith(['P', 'F']));
  });
});
