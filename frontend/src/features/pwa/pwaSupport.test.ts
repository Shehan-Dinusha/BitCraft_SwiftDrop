import { afterEach, describe, expect, it, vi } from 'vitest';
import { isIos, rememberInstallDismissed, wasInstallDismissed } from './pwaSupport';

describe('install banner dismissal', () => {
  afterEach(() => {
    localStorage.clear();
    vi.useRealTimers();
  });

  it('is not dismissed by default', () => {
    expect(wasInstallDismissed()).toBe(false);
  });

  it('stays dismissed for two weeks, then shows again', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-01T00:00:00Z'));
    rememberInstallDismissed();

    vi.setSystemTime(new Date('2026-11-14T00:00:00Z'));
    expect(wasInstallDismissed()).toBe(true);

    vi.setSystemTime(new Date('2026-11-16T00:00:00Z'));
    expect(wasInstallDismissed()).toBe(false);
  });

  it('treats blocked storage as not dismissed instead of throwing', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    expect(() => rememberInstallDismissed()).not.toThrow();
    expect(wasInstallDismissed()).toBe(false);
  });
});

describe('isIos', () => {
  // jsdom's navigator has no maxTouchPoints, so define it per test instead of spying on it.
  const setAgent = (ua: string, touchPoints = 0) => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(ua);
    Object.defineProperty(navigator, 'maxTouchPoints', { value: touchPoints, configurable: true });
  };

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'maxTouchPoints');
  });

  it('detects an iPhone', () => {
    setAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15');
    expect(isIos()).toBe(true);
  });

  it('detects an iPad that reports itself as a Mac', () => {
    setAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15', 5);
    expect(isIos()).toBe(true);
  });

  it('does not flag a desktop Mac or Android', () => {
    setAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15', 0);
    expect(isIos()).toBe(false);
    setAgent('Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/140.0 Mobile');
    expect(isIos()).toBe(false);
  });
});
