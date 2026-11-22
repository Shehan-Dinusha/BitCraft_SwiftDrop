import { describe, expect, it } from 'vitest';
import { centreSquare, sourceProblem } from './avatar';

describe('profile picture helpers', () => {
  it('accepts PNG, JPEG and WebP up to 15 MB only', () => {
    expect(sourceProblem({ type: 'image/jpeg', size: 4_000_000 })).toBeUndefined();
    expect(sourceProblem({ type: 'image/webp', size: 10 })).toBeUndefined();
    expect(sourceProblem({ type: 'image/svg+xml', size: 10 })).toMatch(/PNG, JPEG or WebP/);
    expect(sourceProblem({ type: 'image/gif', size: 10 })).toMatch(/PNG, JPEG or WebP/);
    expect(sourceProblem({ type: 'image/png', size: 16 * 1024 * 1024 })).toMatch(/15 MB/);
  });

  it('crops the centred square of landscape and portrait photos', () => {
    expect(centreSquare(4000, 3000)).toEqual({ sx: 500, sy: 0, side: 3000 });
    expect(centreSquare(1080, 1920)).toEqual({ sx: 0, sy: 420, side: 1080 });
    expect(centreSquare(256, 256)).toEqual({ sx: 0, sy: 0, side: 256 });
  });
});
