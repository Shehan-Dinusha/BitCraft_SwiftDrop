import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { CourierJobResponse, CourierJobStop } from '@/services/api/deliveryApi';
import { JobDetailsCard } from './JobDetailsCard';

const sender: CourierJobStop = {
  name: 'Nimal Perera',
  phone: '+94771234567',
  alternatePhone: null,
  addressLine1: '12 Galle Road',
  addressLine2: null,
  city: 'Colombo 03',
  postalCode: '00300',
  district: 'COLOMBO'
};
const recipient: CourierJobStop = {
  ...sender,
  name: 'Kamala Silva',
  phone: '+94712345678',
  district: 'KANDY'
};

const job = (over: Partial<CourierJobResponse>): CourierJobResponse => ({
  deliveryId: 'd1',
  cancelled: false,
  currency: 'LKR',
  ...over
});

const show = (value: CourierJobResponse, viaHubs: boolean) =>
  render(
    <JobDetailsCard
      job={value}
      viaHubs={viaHubs}
      pickupDistrict="COLOMBO"
      deliveryDistrict="KANDY"
    />
  );

describe('JobDetailsCard', () => {
  it('shows the sender, a call link and the fee to the first courier, and the hub to hand in at', () => {
    show(job({ pickup: sender, feeToCollect: 670 }), true);

    expect(screen.getByText('Nimal Perera')).toBeInTheDocument();
    expect(screen.getByText('12 Galle Road')).toBeInTheDocument();
    expect(screen.getByText('Collect cash from the sender')).toBeInTheDocument();
    expect(screen.getByText('670.00')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Call Nimal Perera/ })).toHaveAttribute(
      'href',
      'tel:+94771234567'
    );
    expect(screen.getByText('Colombo hub')).toBeInTheDocument();
    expect(screen.queryByText('Deliver to')).not.toBeInTheDocument();
  });

  it('sends the last courier from the drop-off hub to the recipient, with the cash on delivery', () => {
    show(job({ dropOff: recipient, codToCollect: 2500 }), true);

    expect(screen.getByText('Kandy hub')).toBeInTheDocument();
    expect(screen.getByText('Kamala Silva')).toBeInTheDocument();
    expect(screen.getByText('Collect cash from the recipient')).toBeInTheDocument();
    expect(screen.queryByText('Hand in at')).not.toBeInTheDocument();
  });

  it('shows no hub on a same-district job and no cash when nothing is due', () => {
    show(job({ dropOff: recipient }), false);

    expect(screen.getByText('Deliver to')).toBeInTheDocument();
    expect(screen.queryByText(/hub/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Collect cash/)).not.toBeInTheDocument();
  });
});
