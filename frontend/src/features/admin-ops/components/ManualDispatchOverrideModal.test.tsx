import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { CourierSuggestionResponse } from '@/services/api/dispatchApi';
import { ManualDispatchOverrideModal } from './ManualDispatchOverrideModal';

const order = {
  orderId: 'o1',
  trackingNumber: 'SD-1',
  lengthCm: 80,
  widthCm: 40,
  heightCm: 30,
  weightKg: 12.5
};
const courier = (over: CourierSuggestionResponse): CourierSuggestionResponse => ({
  courierName: over.courierId,
  activeParcels: 1,
  maxCapacity: 10,
  activeWeightKg: 20.5,
  maxWeightKg: 150,
  activeVolumeLitres: 60,
  maxVolumeLitres: 600,
  maxSideCm: 100,
  vehicleType: 'THREE_WHEELER',
  eligible: true,
  ...over
});
const list = [
  courier({ courierId: 'Nimal' }),
  courier({ courierId: 'Saman', vehicleType: 'VAN', maxWeightKg: 600 }),
  courier({
    courierId: 'Amara',
    vehicleType: 'MOTORCYCLE',
    maxSideCm: 50,
    eligible: false,
    ineligibleReason: 'TOO_LONG'
  })
];

function renderModal(over: Partial<Parameters<typeof ManualDispatchOverrideModal>[0]> = {}) {
  const onSubmit = vi.fn();
  const props = {
    order,
    onClose: vi.fn(),
    suggestions: list,
    loadError: false,
    submitting: false,
    error: null,
    onSubmit,
    ...over
  };
  const view = render(<ManualDispatchOverrideModal {...props} />);
  return {
    onSubmit,
    rerender: (next: Partial<typeof props>) =>
      view.rerender(<ManualDispatchOverrideModal {...props} {...next} />)
  };
}

describe('ManualDispatchOverrideModal', () => {
  it('shows the parcel size, each vehicle and its load, and why a courier is out', () => {
    renderModal();

    expect(screen.getByText('80 × 40 × 30 cm · 12.5 kg')).toBeInTheDocument();
    expect(screen.getByText('20.5 of 600 kg')).toBeInTheDocument();
    expect(screen.getByText('Too long for a motorcycle (up to 50 cm)')).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });

  it('preselects the best match and submits the one chosen', () => {
    const { onSubmit } = renderModal();

    expect(screen.getByRole('radio', { name: /Nimal/ })).toBeChecked();
    fireEvent.click(screen.getByRole('radio', { name: /Saman/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Assign' }));
    expect(onSubmit).toHaveBeenCalledWith('Saman', 'Dispatcher console assignment');
  });

  it('never submits a courier who stopped being eligible after being chosen', () => {
    const { onSubmit, rerender } = renderModal();
    fireEvent.click(screen.getByRole('radio', { name: /Saman/ }));

    rerender({
      suggestions: [list[0], { ...list[1], eligible: false, ineligibleReason: 'WEIGHT_LIMIT' }]
    });
    fireEvent.click(screen.getByRole('button', { name: 'Assign' }));
    expect(onSubmit).toHaveBeenCalledWith('Nimal', expect.any(String));
  });

  it('cannot be submitted when nobody can take the parcel', () => {
    renderModal({ suggestions: [list[2]] });

    expect(screen.getByText('No courier can take this parcel now')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Assign' })).toBeDisabled();
  });
});
