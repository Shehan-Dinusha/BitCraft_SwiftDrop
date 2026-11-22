import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cashApi, type CashReportResponse } from '@/services/api/cashApi';
import { RevenueReport } from './RevenueReport';

const report: CashReportResponse = {
  from: '2026-11-21',
  to: '2026-11-22',
  currency: 'LKR',
  revenue: 770,
  refunds: 350,
  parcels: 3,
  cod: 2500,
  shortage: 70,
  excess: 0,
  withCouriers: 2850,
  toCount: 100,
  atHub: 850,
  toConfirm: 0,
  days: [
    { date: '2026-11-21', revenue: 0, parcels: 0 },
    { date: '2026-11-22', revenue: 770, parcels: 3 }
  ],
  districts: [{ district: 'KANDY', revenue: 0, parcels: 0, lastMileDelivered: 1 }]
};

const show = (showDistricts: boolean) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <RevenueReport from="2026-11-21" to="2026-11-22" showDistricts={showDistricts} />
      </MemoryRouter>
    </QueryClientProvider>
  );

describe('RevenueReport', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    );
  });

  it('keeps revenue, merchant money and cash not banked apart', async () => {
    vi.spyOn(cashApi, 'report').mockResolvedValue(report);
    show(true);

    expect(await screen.findByText('3 parcels, less LKR 350.00 refunded')).toBeInTheDocument();
    expect(screen.getByText('LKR 2,500.00')).toBeInTheDocument();
    expect(screen.getByText('LKR 3,800.00')).toBeInTheDocument();
    expect(screen.getByText('Couriers LKR 2,850.00 · hubs LKR 950.00')).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Kandy/ })).toHaveTextContent('1');
  });

  it('shows a dispatcher no district comparison', async () => {
    vi.spyOn(cashApi, 'report').mockResolvedValue(report);
    show(false);

    expect(await screen.findByText('Revenue per day (LKR)')).toBeInTheDocument();
    expect(screen.queryByText('By district')).not.toBeInTheDocument();
  });

  it('reports its own failure with a retry', async () => {
    vi.spyOn(cashApi, 'report').mockRejectedValue(new Error('down'));
    show(true);

    expect(await screen.findByText('Revenue could not be loaded')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
