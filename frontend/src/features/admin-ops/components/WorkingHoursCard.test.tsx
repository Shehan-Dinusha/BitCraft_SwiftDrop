import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dispatchApi, type WorkdayResponse } from '@/services/api/dispatchApi';
import { WorkingHoursCard, workingDaysLabel } from './WorkingHoursCard';

const standard: WorkdayResponse = {
  date: '2026-11-24',
  open: true,
  nextWorkingDay: '2026-11-25',
  shiftCanStart: true,
  workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
  opensAt: '08:00:00',
  closesAt: '17:00:00',
  shiftFrom: '07:30:00',
  lateAfter: '09:00:00',
  breakFrom: '12:00:00',
  breakUntil: '14:30:00',
  breakMinutes: 45,
  pickupCutoff: '15:00:00',
  sameDayCutoff: '11:00:00',
  closedDays: []
};

const show = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <WorkingHoursCard hours={standard} />
    </QueryClientProvider>
  );

describe('workingDaysLabel', () => {
  it('names a run of days by its ends and lists the rest', () => {
    expect(workingDaysLabel(standard.workingDays)).toBe('Monday to Saturday');
    expect(workingDaysLabel(['MONDAY', 'WEDNESDAY', 'FRIDAY'])).toBe('Monday, Wednesday, Friday');
    expect(workingDaysLabel([])).toBe('No working days');
  });
});

describe('WorkingHoursCard', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('shows the current hours', () => {
    show();
    expect(screen.getByText(/Monday to Saturday, 08:00 to 17:00/)).toBeInTheDocument();
    expect(screen.getByText('45 min, from 12:00 to 14:30')).toBeInTheDocument();
    expect(screen.getByText('11:00')).toBeInTheDocument();
  });

  it('saves edited days and times', async () => {
    const save = vi
      .spyOn(dispatchApi, 'updateWorkingHours')
      .mockResolvedValue({
        ...standard,
        workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
      });
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Edit hours' }));
    fireEvent.click(screen.getByRole('button', { name: 'Saturday' }));
    fireEvent.change(screen.getByLabelText(/^Closes/), { target: { value: '18:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save hours' }));

    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(save.mock.calls[0][0]).toMatchObject({
      workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      opensAt: '08:00',
      closesAt: '18:00',
      breakMinutes: 45
    });
    expect(await screen.findByText(/Working hours saved/)).toBeInTheDocument();
  });

  it('will not save without a working day', () => {
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Edit hours' }));
    for (const day of ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']) {
      fireEvent.click(screen.getByRole('button', { name: day }));
    }
    expect(screen.getByText('Choose at least one day.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save hours' })).toBeDisabled();
  });

  it('restores the standard hours', async () => {
    const reset = vi.spyOn(dispatchApi, 'resetWorkingHours').mockResolvedValue(standard);
    show();
    fireEvent.click(screen.getByRole('button', { name: /Restore standard/ }));
    await waitFor(() => expect(reset).toHaveBeenCalled());
    expect(await screen.findByText(/standard working hours are back/)).toBeInTheDocument();
  });
});
