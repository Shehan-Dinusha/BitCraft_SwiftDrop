import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { notificationApi } from '@/services/api/notificationApi';
import {
  PushSetupError,
  base64UrlToBytes,
  disableDevicePush,
  enableDevicePush,
  forgetDeviceOnSignOut,
  pushSupport
} from './webPush';

const KEY = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U';

function fakeSubscription(endpoint: string, key: Uint8Array | null) {
  return {
    endpoint,
    options: { applicationServerKey: key ? key.buffer : null },
    toJSON: () => ({ endpoint, keys: { p256dh: 'p256dh-key', auth: 'auth-key' } }),
    unsubscribe: vi.fn().mockResolvedValue(true)
  };
}

let existing: ReturnType<typeof fakeSubscription> | null;
const subscribe = vi.fn();
const requestPermission = vi.fn();

function installBrowser(permission: NotificationPermission = 'default') {
  existing = null;
  subscribe.mockReset().mockImplementation(async () => {
    existing = fakeSubscription('https://fcm.googleapis.com/fcm/send/new', base64UrlToBytes(KEY));
    return existing;
  });
  requestPermission.mockReset().mockResolvedValue('granted');
  const registration = {
    pushManager: { getSubscription: vi.fn(async () => existing), subscribe }
  };
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: { getRegistration: vi.fn(async () => registration) }
  });
  vi.stubGlobal('PushManager', function PushManager() {});
  vi.stubGlobal('Notification', Object.assign(function Notification() {}, { permission, requestPermission }));
}

describe('lock-screen notification helpers', () => {
  beforeEach(() => {
    installBrowser();
    vi.spyOn(notificationApi, 'getPushPublicKey').mockResolvedValue({ enabled: true, publicKey: KEY });
    vi.spyOn(notificationApi, 'registerPushSubscription').mockResolvedValue();
    vi.spyOn(notificationApi, 'removePushSubscription').mockResolvedValue();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'serviceWorker');
  });

  it('decodes the base64url VAPID key into the 65 raw bytes', () => {
    const bytes = base64UrlToBytes(KEY);
    expect(bytes).toHaveLength(65);
    expect(bytes[0]).toBe(0x04);
  });

  it('asks permission, subscribes with the server key and registers the device', async () => {
    await enableDevicePush();

    expect(requestPermission).toHaveBeenCalledTimes(1);
    expect(subscribe).toHaveBeenCalledWith({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(KEY) });
    expect(notificationApi.registerPushSubscription).toHaveBeenCalledWith({
      endpoint: 'https://fcm.googleapis.com/fcm/send/new',
      keys: { p256dh: 'p256dh-key', auth: 'auth-key' }
    });
  });

  it('replaces a subscription made with old server keys', async () => {
    const stale = fakeSubscription('https://fcm.googleapis.com/fcm/send/old', new Uint8Array([4, 1, 2]));
    existing = stale;

    await enableDevicePush();

    expect(stale.unsubscribe).toHaveBeenCalled();
    expect(subscribe).toHaveBeenCalled();
  });

  it('stops when the user blocks notifications', async () => {
    requestPermission.mockResolvedValue('denied');

    await expect(enableDevicePush()).rejects.toMatchObject({ reason: 'denied' });
    expect(subscribe).not.toHaveBeenCalled();
    expect(notificationApi.registerPushSubscription).not.toHaveBeenCalled();
  });

  it('reports a server without keys instead of subscribing', async () => {
    vi.mocked(notificationApi.getPushPublicKey).mockResolvedValue({ enabled: false, publicKey: null });

    await expect(enableDevicePush()).rejects.toBeInstanceOf(PushSetupError);
    expect(subscribe).not.toHaveBeenCalled();
  });

  it('turning off removes the device on the server, then in the browser', async () => {
    const sub = fakeSubscription('https://fcm.googleapis.com/fcm/send/mine', base64UrlToBytes(KEY));
    existing = sub;

    await disableDevicePush();

    expect(notificationApi.removePushSubscription).toHaveBeenCalledWith('https://fcm.googleapis.com/fcm/send/mine');
    expect(sub.unsubscribe).toHaveBeenCalled();
  });

  it('sign-out still unsubscribes the browser when the server cannot be reached', async () => {
    const sub = fakeSubscription('https://fcm.googleapis.com/fcm/send/mine', base64UrlToBytes(KEY));
    existing = sub;
    vi.mocked(notificationApi.removePushSubscription).mockRejectedValue(new Error('offline'));

    await expect(forgetDeviceOnSignOut()).resolves.toBeUndefined();
    expect(sub.unsubscribe).toHaveBeenCalled();
  });

  it('sign-out never waits longer than the timeout', async () => {
    existing = fakeSubscription('https://fcm.googleapis.com/fcm/send/mine', base64UrlToBytes(KEY));
    vi.mocked(notificationApi.removePushSubscription).mockReturnValue(new Promise(() => {}));
    const started = Date.now();

    await forgetDeviceOnSignOut(50);

    expect(Date.now() - started).toBeLessThan(1000);
  });

  it('after the account is deleted only the browser side is dropped', async () => {
    const sub = fakeSubscription('https://fcm.googleapis.com/fcm/send/mine', base64UrlToBytes(KEY));
    existing = sub;

    await forgetDeviceOnSignOut(3000, { tellServer: false });

    expect(notificationApi.removePushSubscription).not.toHaveBeenCalled();
    expect(sub.unsubscribe).toHaveBeenCalled();
  });

  it('on iPhone outside the Home Screen app, push is reported as needing install', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Mobile/15E148 Safari/604.1'
    );
    expect(pushSupport()).toBe('ios-needs-install');
  });
});
