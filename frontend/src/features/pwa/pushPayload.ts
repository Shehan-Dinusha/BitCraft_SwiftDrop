/**
 * Shared by the service worker (src/sw.ts) and the tests: how a push message is read and where a tap
 * on it may go. No DOM or worker globals here, so it runs in both.
 */

export interface PushNotice {
  title: string;
  body: string;
  /** Same-origin path to open when the notification is tapped. */
  url: string;
  /** Tracking number: a newer update replaces the older one on the lock screen. */
  tag?: string;
}

const FALLBACK: PushNotice = { title: 'SwiftDrop', body: 'You have a new update.', url: '/' };

function text(value: unknown, max: number): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim().slice(0, max) : undefined;
}

/**
 * The server sends {title, body, url, tag}. Anything unreadable still shows a generic notification:
 * with userVisibleOnly every push must show one, or the browser may revoke the subscription.
 */
export function parsePushPayload(raw: string | null | undefined): PushNotice {
  if (!raw) return FALLBACK;
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    return {
      title: text(data.title, 80) ?? FALLBACK.title,
      body: text(data.body, 200) ?? FALLBACK.body,
      url: text(data.url, 300) ?? FALLBACK.url,
      tag: text(data.tag, 64)
    };
  } catch {
    return FALLBACK;
  }
}

/**
 * Where a tap may go: only a path on our own origin. Anything else (another site, javascript:,
 * protocol-relative //evil.example) falls back to the home page.
 */
export function safeTarget(url: string | undefined, origin: string): string {
  if (!url) return `${origin}/`;
  try {
    const target = new URL(url, origin);
    return target.origin === origin ? target.href : `${origin}/`;
  } catch {
    return `${origin}/`;
  }
}
