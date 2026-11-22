import { describe, expect, it } from 'vitest';
import { shouldDock } from './useAssistantDocked';

const button = { top: 700, bottom: 748, left: 1200, right: 1360 };
const row = (top: number, left = 280, right = 1400) => ({ top, bottom: top + 44, left, right });

describe('shouldDock', () => {
  it('floats while the page continues below the screen', () => {
    expect(shouldDock(button, [row(1200)], 2000)).toBe(false);
  });

  it('moves to the header when a button row reaches it, and back when the row leaves', () => {
    expect(shouldDock(button, [row(720)], 2000)).toBe(true);
    expect(shouldDock(button, [row(800)], 2000)).toBe(false);
  });

  it('ignores rows beside it and rows that are not shown', () => {
    expect(shouldDock(button, [row(720, 280, 900)], 2000)).toBe(false);
    expect(shouldDock(button, [{ top: 0, bottom: 0, left: 0, right: 0 }], 2000)).toBe(false);
  });

  it('moves to the header at the end of the page', () => {
    expect(shouldDock(button, [], 764)).toBe(true);
  });

  it('floats on a short page that ends above it', () => {
    expect(shouldDock(button, [], 400)).toBe(false);
  });
});
