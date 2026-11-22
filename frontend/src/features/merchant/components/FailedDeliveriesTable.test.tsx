import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { webhookApi, type FailedDeliveryResponse } from '@/services/api/webhookApi';
import { FailedDeliveriesTable } from './FailedDeliveriesTable';

const failure = (over: FailedDeliveryResponse): FailedDeliveryResponse => ({
  eventType: 'parcel.status.changed',
  errorMessage: 'Endpoint returned a server error (HTTP 503)',
  failedAt: '2026-11-22T06:00:00Z',
  ...over
});

function renderTable(rows: FailedDeliveryResponse[]) {
  vi.spyOn(webhookApi, 'listFailedDeliveries').mockResolvedValue(rows);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <FailedDeliveriesTable />
    </QueryClientProvider>
  );
}

describe('FailedDeliveriesTable', () => {
  afterEach(() => vi.restoreAllMocks());

  it('offers Replay only for events that are not delivered or on their way', async () => {
    renderTable([
      failure({ deliveryId: 'a', trackingNumber: 'SD-A' }),
      failure({ deliveryId: 'b', trackingNumber: 'SD-B', replayStatus: 'PENDING' }),
      failure({ deliveryId: 'c', trackingNumber: 'SD-C', replayStatus: 'SUCCEEDED' }),
      failure({ deliveryId: 'd', trackingNumber: 'SD-D', replayStatus: 'DEAD' })
    ]);

    expect(await screen.findByRole('button', { name: /Replay .* SD-A/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Replay .* SD-D/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /SD-B/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /SD-C/ })).not.toBeInTheDocument();
    expect(screen.getByText('Sending again')).toBeInTheDocument();
    expect(screen.getByText('Delivered on replay')).toBeInTheDocument();
    expect(screen.getByText('Replay failed')).toBeInTheDocument();
  });

  it('replays one event, or all of them', async () => {
    const one = vi.spyOn(webhookApi, 'replayFailedDelivery').mockResolvedValue({});
    const all = vi.spyOn(webhookApi, 'replayAllFailedDeliveries').mockResolvedValue(2);
    renderTable([
      failure({ deliveryId: 'a', trackingNumber: 'SD-A' }),
      failure({ deliveryId: 'b', trackingNumber: 'SD-B' })
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /Replay .* SD-A/ }));
    await waitFor(() => expect(one).toHaveBeenCalledWith('a'));

    const replayAll = screen.getByRole('button', { name: 'Replay all (2)' });
    await waitFor(() => expect(replayAll).toBeEnabled());
    fireEvent.click(replayAll);
    expect(await screen.findByText('Sending 2 events again.')).toBeInTheDocument();
    expect(all).toHaveBeenCalledTimes(1);
  });

  it('says so when there is nothing to replay', async () => {
    renderTable([]);

    expect(await screen.findByText('No failed events.')).toBeInTheDocument();
  });
});
