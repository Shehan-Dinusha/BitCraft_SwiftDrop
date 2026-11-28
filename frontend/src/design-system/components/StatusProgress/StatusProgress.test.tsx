import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusProgress } from './StatusProgress';

describe('StatusProgress', () => {
  it('names every step in both layouts, including the ones still to come', () => {
    render(<StatusProgress current="IN_TRANSIT" />);
    const lists = screen.getAllByRole('list', { hidden: true });
    expect(lists).toHaveLength(2);
    for (const list of lists) {
      const steps = within(list).getAllByRole('listitem', { hidden: true });
      expect(steps.map((step) => step.textContent)).toEqual([
        'Created',
        'Picked Up',
        'In Transit',
        'Out for Delivery',
        'Delivered'
      ]);
      expect(steps.map((step) => step.dataset.state)).toEqual([
        'done',
        'done',
        'current',
        'upcoming',
        'upcoming'
      ]);
    }
  });

  it('ticks every step once delivered', () => {
    render(<StatusProgress current="DELIVERED" />);
    const [phone] = screen.getAllByRole('list', { hidden: true });
    const states = within(phone)
      .getAllByRole('listitem', { hidden: true })
      .map((step) => step.dataset.state);
    expect(states).toEqual(['done', 'done', 'done', 'done', 'done']);
  });
});
