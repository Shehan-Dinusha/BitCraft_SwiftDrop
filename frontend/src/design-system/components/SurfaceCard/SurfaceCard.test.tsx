import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SurfaceCard } from './SurfaceCard';

describe('SurfaceCard', () => {
  it('puts bodyClassName on the element that holds the content, so spacing reaches it', () => {
    render(
      <SurfaceCard className="p-4" bodyClassName="space-y-3">
        <button type="button">Hand in</button>
        <button type="button">Start run</button>
      </SurfaceCard>
    );
    const body = screen.getByRole('button', { name: 'Hand in' }).parentElement;
    expect(body).toHaveClass('space-y-3');
    expect(body).toContainElement(screen.getByRole('button', { name: 'Start run' }));
    expect(body?.parentElement).toHaveClass('p-4');
    expect(body?.parentElement).not.toHaveClass('space-y-3');
  });
});
