import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { deliveryApi } from '@/services/api/deliveryApi';
import { dispatchApi, type DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { useAuthStore } from '@/store/useAuthStore';
import { DispatchBoardPage } from './DispatchBoardPage';

const order = (id: string, over: DispatchAssignmentResponse = {}): DispatchAssignmentResponse => ({
  orderId: id,
  trackingNumber: `SD-${id}`,
  pickupDistrict: 'COLOMBO',
  deliveryDistrict: 'KANDY',
  orderSpeed: 'STANDARD',
  createdAt: '2026-11-22T08:00:00Z',
  ...over
});

const page = (content: DispatchAssignmentResponse[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false
});

function renderPage(path = '/ops/dispatch') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <DispatchBoardPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const signInAs = (role: 'DISPATCHER' | 'SYSTEM_ADMIN') =>
  useAuthStore.setState({
    user: { id: 'u1', role, district: role === 'DISPATCHER' ? 'COLOMBO' : undefined } as never
  });

describe('DispatchBoardPage', () => {
  beforeEach(() => {
    signInAs('DISPATCHER');
    vi.spyOn(dispatchApi, 'getUnassignedParcels').mockResolvedValue([
      order('A'),
      order('B', { orderSpeed: 'EXPRESS' })
    ]);
    vi.spyOn(dispatchApi, 'getAssignments').mockImplementation(async (params) =>
      params?.status === 'ASSIGNED'
        ? page([
            order('C', { courierId: 'c1', courierName: 'Nimal' }),
            order('D', {
              courierId: 'c1',
              courierName: 'Nimal',
              pickedUpAt: '2026-11-22T09:00:00Z'
            })
          ])
        : page([])
    );
    vi.spyOn(dispatchApi, 'getReadyToSend').mockResolvedValue([
      order('E', { hubStage: 'AT_HUB' }),
      order('F', { hubStage: 'AT_HUB' }),
      order('G', { hubStage: 'AT_HUB', deliveryDistrict: 'GALLE' })
    ]);
    vi.spyOn(dispatchApi, 'getIncoming').mockResolvedValue([
      order('H', { hubStage: 'OUTBOUND', pickupDistrict: 'GALLE', deliveryDistrict: 'COLOMBO' }),
      order('I', { pickupDistrict: 'MATARA', deliveryDistrict: 'COLOMBO' })
    ]);
    vi.spyOn(dispatchApi, 'getReturns').mockResolvedValue([
      order('J', {
        hubStage: 'HELD_AT_HUB',
        pickupDistrict: 'KANDY',
        deliveryDistrict: 'COLOMBO',
        deliveryAttempts: 1,
        lastAttemptReason: 'RECIPIENT_UNAVAILABLE',
        canRetry: true
      }),
      order('K', {
        hubStage: 'HELD_AT_HUB',
        pickupDistrict: 'COLOMBO',
        deliveryDistrict: 'COLOMBO',
        deliveryAttempts: 2,
        lastAttemptReason: 'RECIPIENT_REJECTED',
        canRetry: false
      }),
      order('L', { hubStage: 'RETURN_OUTBOUND', leg: 'RETURN', deliveryDistrict: 'GALLE' })
    ]);
    vi.spyOn(dispatchApi, 'getCouriers').mockResolvedValue([]);
  });

  it('shows one queue at a time with the counts on the tabs', async () => {
    renderPage();

    expect(await screen.findByText('SD-A')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'To assign 2' })).toHaveAttribute(
      'aria-selected',
      'true'
    );
    expect(await screen.findByRole('tab', { name: 'Ready to send 3' })).toBeInTheDocument();
    expect(screen.queryByText('SD-E')).not.toBeInTheDocument();
    // Express is listed above the standard order.
    const rows = within(screen.getByRole('table', { name: 'Orders to assign' })).getAllByRole(
      'row'
    );
    expect(rows[1]).toHaveTextContent('SD-B');
  });

  it('keeps the assign dialog open with fresh couriers when the server refuses', async () => {
    const suggest = vi
      .spyOn(dispatchApi, 'getCourierSuggestions')
      .mockResolvedValue([{ courierId: 'c1', courierName: 'Nimal', eligible: true }]);
    vi.spyOn(dispatchApi, 'manualAssign').mockRejectedValue(
      new Error('Courier Nimal already carries 24 of 25 kg')
    );
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Assign SD-A' }));
    await screen.findByRole('radio', { name: /Nimal/ });
    fireEvent.click(screen.getByRole('button', { name: 'Assign' }));

    expect(await screen.findByText('Courier Nimal already carries 24 of 25 kg')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(suggest).toHaveBeenCalledTimes(2));
  });

  it('opens the tab in the URL and prefills the search from a link', async () => {
    renderPage('/ops/dispatch?tab=assigned&q=SD-C');

    expect(await screen.findByText('SD-C')).toBeInTheDocument();
    expect(screen.queryByText('SD-D')).not.toBeInTheDocument();
  });

  it('offers no reassignment once the parcel is picked up', async () => {
    renderPage('/ops/dispatch?tab=assigned');

    expect(await screen.findByRole('button', { name: 'Reassign SD-C' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reassign SD-D' })).not.toBeInTheDocument();
  });

  it('marks a whole district outbound at once and reports the parcel that failed', async () => {
    vi.spyOn(deliveryApi, 'getDeliveryByOrderId').mockImplementation(async (orderId) => ({
      deliveryId: `d-${orderId}`
    }));
    const transition = vi
      .spyOn(deliveryApi, 'transitionDelivery')
      .mockImplementation(async (id) => {
        if (id === 'd-F') throw new Error('Not at the hub');
        return {};
      });
    renderPage('/ops/dispatch?tab=ready');

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Select all to Kandy' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mark outbound (2)' }));

    expect(await screen.findByText('1 parcel marked outbound, 1 failed')).toBeInTheDocument();
    expect(screen.getByText('SD-F: Not at the hub')).toBeInTheDocument();
    expect(transition).toHaveBeenCalledTimes(2);
    // The one that went is hidden at once, before dispatch hears about it.
    await waitFor(() => expect(screen.queryByText('SD-E')).not.toBeInTheDocument());
    expect(screen.getByText('SD-F')).toBeInTheDocument();
  });

  it('accepts only parcels that are on the way', async () => {
    renderPage('/ops/dispatch?tab=incoming');

    expect(await screen.findByRole('checkbox', { name: 'Select SD-H' })).toBeInTheDocument();
    expect(screen.getByText('Not sent yet (1)')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: 'Select SD-I' })).not.toBeInTheDocument();
  });

  it('decides on failed deliveries, and accepts returns arriving at the hub', async () => {
    vi.spyOn(deliveryApi, 'getDeliveryByOrderId').mockImplementation(async (orderId) => ({
      deliveryId: `d-${orderId}`
    }));
    const transition = vi.spyOn(deliveryApi, 'transitionDelivery').mockResolvedValue({});
    renderPage('/ops/dispatch?tab=returns');

    // Refused twice over: no second attempt, only the return.
    expect(await screen.findByText('Recipient refused it')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try SD-K again' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Return SD-J to the sender' })).toHaveTextContent(
      'Send back to Kandy'
    );

    fireEvent.click(screen.getByRole('button', { name: 'Try SD-J again' }));
    expect(await screen.findByText('1 parcel ready to go out again')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('SD-J')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Return SD-K to the sender' }));
    expect(await screen.findByText('1 parcel ready to return')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: 'Select SD-L' }));
    fireEvent.click(screen.getByRole('button', { name: 'Accept at hub (1)' }));
    await waitFor(() => expect(screen.queryByText('SD-L')).not.toBeInTheDocument());

    expect(transition.mock.calls.map(([id, body]) => [id, body.targetState])).toEqual([
      ['d-J', 'AT_DESTINATION_HUB'],
      ['d-K', 'RETURN_AT_HUB'],
      ['d-L', 'RETURN_AT_HUB']
    ]);
  });

  it('records a lost parcel, so its fee can be refunded', async () => {
    vi.spyOn(deliveryApi, 'getDeliveryByOrderId').mockResolvedValue({ deliveryId: 'd-J' });
    const transition = vi.spyOn(deliveryApi, 'transitionDelivery').mockResolvedValue({});
    renderPage('/ops/dispatch?tab=returns');

    fireEvent.click(await screen.findByRole('button', { name: 'SD-J is lost or damaged' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Parcel lost' }));

    await waitFor(() =>
      expect(transition).toHaveBeenCalledWith(
        'd-J',
        expect.objectContaining({ targetState: 'FAILED', failureReason: 'LOST' })
      )
    );
  });

  it('hides the hub tabs until an admin picks a district', async () => {
    signInAs('SYSTEM_ADMIN');
    renderPage('/ops/dispatch?tab=ready');

    expect(await screen.findByText('SD-A')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Ready to send/ })).not.toBeInTheDocument();
    expect(dispatchApi.getReadyToSend).not.toHaveBeenCalled();
  });

  it('does not call a queue empty when it could not be loaded', async () => {
    vi.spyOn(dispatchApi, 'getUnassignedParcels').mockRejectedValue(new Error('down'));
    renderPage();

    expect(
      await screen.findByText('Some lists could not be refreshed', undefined, { timeout: 4000 })
    ).toBeInTheDocument();
    expect(screen.queryByText('No orders to assign')).not.toBeInTheDocument();
  });
});
