import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FeePanel } from './ReviewStep';

describe('FeePanel', () => {
  it('shows the promised day with the price', () => {
    render(
      <FeePanel
        estimate={{ estimatedFee: 450, baseFee: 350, weightFee: 100, deliverBy: '2026-11-26' }}
        status="ready"
        onRetry={vi.fn()}
        speedLabel="Standard"
      />
    );
    expect(screen.getByText('Delivery by Thursday 26 November')).toBeInTheDocument();
  });

  it('says why Same day cannot be booked, with no retry', () => {
    render(
      <FeePanel
        estimate={null}
        status="refused"
        problem="Same day delivery is only within one district."
        onRetry={vi.fn()}
        speedLabel="Same day"
      />
    );
    expect(screen.getByText('Same day is not available')).toBeInTheDocument();
    expect(screen.getByText('Same day delivery is only within one district.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });
});
