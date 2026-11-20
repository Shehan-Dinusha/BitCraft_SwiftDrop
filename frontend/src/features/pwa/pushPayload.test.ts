import { describe, expect, it } from 'vitest';
import { parsePushPayload, safeTarget } from './pushPayload';

const ORIGIN = 'https://swiftdrop.example';

describe('push payload', () => {
  it('reads the server message', () => {
    expect(
      parsePushPayload(
        JSON.stringify({
          title: 'Out for delivery',
          body: 'SD-1234-5678 is on its way',
          url: '/customer/tracking/SD-1234-5678',
          tag: 'SD-1234-5678'
        })
      )
    ).toEqual({
      title: 'Out for delivery',
      body: 'SD-1234-5678 is on its way',
      url: '/customer/tracking/SD-1234-5678',
      tag: 'SD-1234-5678'
    });
  });

  it('still shows a generic notification for empty or broken messages', () => {
    for (const raw of [undefined, null, '', 'not json', '[]', '{"title": 42}']) {
      const notice = parsePushPayload(raw);
      expect(notice.title).toBe('SwiftDrop');
      expect(notice.url).toBe('/');
    }
  });

  it('caps very long text', () => {
    expect(parsePushPayload(JSON.stringify({ title: 'x'.repeat(500) })).title).toHaveLength(80);
  });
});

describe('notification click target', () => {
  it('opens paths on our own origin', () => {
    expect(safeTarget('/courier/deliveries', ORIGIN)).toBe(`${ORIGIN}/courier/deliveries`);
    expect(safeTarget(`${ORIGIN}/customer/tracking/SD-1`, ORIGIN)).toBe(`${ORIGIN}/customer/tracking/SD-1`);
  });

  it('never follows another origin or a script URL', () => {
    for (const url of ['https://evil.example/phish', '//evil.example/x', 'javascript:alert(1)', 'data:text/html,hi']) {
      expect(safeTarget(url, ORIGIN)).toBe(`${ORIGIN}/`);
    }
    expect(safeTarget(undefined, ORIGIN)).toBe(`${ORIGIN}/`);
  });
});
