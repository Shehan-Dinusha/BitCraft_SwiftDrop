import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { CustomerOrderResponse } from '@/services/api/orderApi';
import { OrderFeeSection } from './OrderFeeSection';

const order = (status: CustomerOrderResponse['status']): CustomerOrderResponse =>
  ({ status, estimatedFee: 450, currency: 'LKR' }) as CustomerOrderResponse;

describe('OrderFeeSection', () => {
  it('asks for the fee until the courier picks the parcel up', () => {
    render(<OrderFeeSection order={order('PICKUP_ASSIGNED')} />);
    expect(screen.getByText('To pay')).toBeInTheDocument();
    expect(screen.getByText('Pay the courier in cash at pickup.')).toBeInTheDocument();
  });

  it('shows the fee as paid once the parcel is picked up', () => {
    for (const status of ['PICKED_UP', 'IN_TRANSIT', 'DELIVERED'] as const) {
      const { unmount } = render(<OrderFeeSection order={order(status)} />);
      expect(screen.getByText('Paid')).toBeInTheDocument();
      expect(screen.queryByText('To pay')).not.toBeInTheDocument();
      expect(screen.getByText('Paid to the courier in cash at pickup.')).toBeInTheDocument();
      unmount();
    }
  });
});
