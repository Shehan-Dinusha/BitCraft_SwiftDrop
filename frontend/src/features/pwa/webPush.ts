import { notificationApi } from '@/services/api/notificationApi';
import { isIos, isStandalone } from './pwaSupport';

/**
 * Lock-screen (Web Push) notifications for this device.
 *
 * - `unsupported`: the browser has no service worker, Push API or Notification API.
 * - `ios-needs-install`: iPhone and iPad only allow push from an app added to the Home Screen (iOS 16.4+).
 * - `supported`: can be turned on (after the user taps, never on page load).
 */
export type PushSupport = 'supported' | 'unsupported' | 'ios-needs-install';

export function pushSupport(): PushSupport {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return 'unsupported';
  if (isIos() && !isStandalone()) return 'ios-needs-install';
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
    return 'unsupported';
  }
  return 'supported';
}

/** 'default' | 'granted' | 'denied', or 'denied' where notifications do not exist. */
export function notificationPermission(): NotificationPermission {
  return typeof Notification === 'undefined' ? 'denied' : Notification.permission;
}

/** VAPID keys are base64url; pushManager.subscribe wants the raw bytes. */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  const binary = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function sameKey(current: ArrayBuffer | null | undefined, wanted: Uint8Array): boolean {
  if (!current) return false;
  const bytes = new Uint8Array(current);
  return bytes.length === wanted.length && bytes.every((b, i) => b === wanted[i]);
}

/**
 * The service worker registration, without waiting: `navigator.serviceWorker.ready` never resolves
 * where no worker is registered (for example `npm run dev`).
 */
async function registration(): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigator)) return undefined;
  return navigator.serviceWorker.getRegistration();
}

/** This device's current subscription, if any. */
export async function currentSubscription(): Promise<PushSubscription | null> {
  const reg = await registration();
  return reg ? reg.pushManager.getSubscription() : null;
}

export class PushSetupError extends Error {
  constructor(
    readonly reason: 'unsupported' | 'denied' | 'unavailable' | 'no-worker',
    message: string
  ) {
    super(message);
  }
}

/**
 * Turns lock-screen notifications on for this device. Call only from a click or tap: it may show
 * the browser's permission prompt.
 */
export async function enableDevicePush(): Promise<void> {
  if (pushSupport() !== 'supported') {
    throw new PushSetupError('unsupported', 'This browser cannot show notifications.');
  }
  const permission = notificationPermission() === 'granted' ? 'granted' : await Notification.requestPermission();
  if (permission !== 'granted') {
    throw new PushSetupError('denied', 'Notifications are blocked for SwiftDrop in this browser.');
  }
  const { enabled, publicKey } = await notificationApi.getPushPublicKey();
  if (!enabled || !publicKey) {
    throw new PushSetupError('unavailable', 'Notifications are not available on this server yet.');
  }
  const reg = await registration();
  if (!reg) {
    throw new PushSetupError('no-worker', 'Reload SwiftDrop once, then try again.');
  }
  const key = base64UrlToBytes(publicKey);
  let subscription = await reg.pushManager.getSubscription();
  if (subscription && !sameKey(subscription.options.applicationServerKey, key)) {
    // The server's keys changed: the old subscription can never receive our messages.
    await subscription.unsubscribe();
    subscription = null;
  }
  subscription ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  const json = subscription.toJSON();
  await notificationApi.registerPushSubscription({
    endpoint: json.endpoint ?? subscription.endpoint,
    keys: { p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' }
  });
}

/** Turns lock-screen notifications off for this device (server first, then the browser). */
export async function disableDevicePush(): Promise<void> {
  const subscription = await currentSubscription();
  if (!subscription) return;
  try {
    await notificationApi.removePushSubscription(subscription.endpoint);
  } finally {
    await subscription.unsubscribe();
  }
}

/**
 * On sign-out, before the session is cleared: the next person on a shared phone must not get the
 * previous user's alerts. Never throws and never holds sign-out up for more than a few seconds.
 */
export async function forgetDeviceOnSignOut(timeoutMs = 3000, { tellServer = true } = {}): Promise<void> {
  try {
    await Promise.race([
      // Without a valid session only the browser side can go; the server then gets 410 from the
      // push service on the next message and deletes the row itself.
      tellServer ? disableDevicePush() : currentSubscription().then((sub) => sub?.unsubscribe()),
      new Promise<void>((resolve) => setTimeout(resolve, timeoutMs))
    ]);
  } catch {
    // Offline or already removed: the browser side is still unsubscribed by disableDevicePush.
  }
}
