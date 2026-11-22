import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/store/useAuthStore';
import { cashApi, type CashDeskResponse, type CashHandoverResponse } from '@/services/api/cashApi';
import { CashDeskPage } from './CashDeskPage';

const handover: CashHandoverResponse = {
  handoverId: 'h1',
  courierId: 'c1',
  courierName: 'Nuwan Silva',
  district: 'COLOMBO',
  expectedAmount: 770,
  currency: 'LKR',
  status: 'SUBMITTED',
  submittedAt: '2026-11-22T10:00:00Z'
};

const desk: CashDeskResponse = {
  district: 'COLOMBO',
  currency: 'LKR',
  withCouriers: [],
  toCount: [handover],
  atHub: [],
  refunds: [],
  hubCash: 0,
  deposits: [
    {
      depositId: 'd1',
      district: 'COLOMBO',
      amount: 25400,
      currency: 'LKR',
      reference: 'BOC-884213',
      status: 'SUBMITTED',
      depositedAt: '2026-11-22T10:00:00Z'
    }
  ]
};

const openAs = (role: 'DISPATCHER' | 'SYSTEM_ADMIN', path = '/ops/cash') => {
  useAuthStore.setState({ user: { id: 'u1', role, district: 'COLOMBO' }, role: 'ADMIN_OPS' });
  return render(
    <MemoryRouter initialEntries={[path]}>
      <CashDeskPage />
    </MemoryRouter>
  );
};

describe('CashDeskPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(cashApi, 'desk').mockResolvedValue(desk);
  });

  it('saves a different count only with a note', async () => {
    const count = vi.spyOn(cashApi, 'count').mockResolvedValue(handover);
    openAs('DISPATCHER');
    fireEvent.click(await screen.findByRole('button', { name: 'Count' }));
    const dialog = within(await screen.findByRole('dialog'));
    const save = dialog.getByRole('button', { name: 'Save count' });
    expect(save).toBeDisabled();

    fireEvent.change(dialog.getByLabelText(/Amount counted/), { target: { value: '700' } });
    expect(dialog.getByText('Short by LKR 70.00')).toBeInTheDocument();
    expect(save).toBeDisabled();
    fireEvent.change(dialog.getByLabelText(/Why it differs/), { target: { value: '70 missing' } });
    fireEvent.click(save);

    await waitFor(() =>
      expect(count).toHaveBeenCalledWith('h1', { countedAmount: 700, note: '70 missing' })
    );
  });

  it('keeps the dialog open with the reason when the count is refused', async () => {
    vi.spyOn(cashApi, 'count').mockRejectedValue(new Error('This handover was already counted.'));
    openAs('DISPATCHER');
    fireEvent.click(await screen.findByRole('button', { name: 'Count' }));
    const dialog = within(await screen.findByRole('dialog'));
    fireEvent.change(dialog.getByLabelText(/Amount counted/), { target: { value: '770' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save count' }));

    expect(await dialog.findByRole('alert')).toHaveTextContent('already counted');
  });

  it('lets only an admin confirm a deposit, and an admin never counts', async () => {
    const dispatcher = openAs('DISPATCHER', '/ops/cash?tab=deposits');
    expect(await screen.findByText('BOC-884213')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
    dispatcher.unmount();

    const admin = openAs('SYSTEM_ADMIN', '/ops/cash?tab=deposits');
    expect(await screen.findByRole('button', { name: 'Confirm' })).toBeInTheDocument();
    admin.unmount();

    openAs('SYSTEM_ADMIN', '/ops/cash?tab=count');
    expect(await screen.findByText('Nuwan Silva')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Count' })).not.toBeInTheDocument();
  });
});
