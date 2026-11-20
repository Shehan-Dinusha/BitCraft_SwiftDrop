import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { notificationApi } from '@/services/api/notificationApi';
import { DevicePushRow } from './DevicePushRow';

const KEY = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U';
const requestPermission = vi.fn();

function installBrowser(permission: NotificationPermission) {
  const subscription = {
    endpoint: 'https://fcm.googleapis.com/fcm/send/x',
    options: { applicationServerKey: null },
    toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/fcm/send/x', keys: { p256dh: 'p', auth: 'a' } }),
    unsubscribe: vi.fn()
  };
  const registration = {
    pushManager: { getSubscription: vi.fn(async () => null), subscribe: vi.fn(async () => subscription) }
  };
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: { getRegistration: vi.fn(async () => registration) }
  });
  vi.stubGlobal('PushManager', function PushManager() {});
  requestPermission.mockReset().mockResolvedValue('granted');
  vi.stubGlobal('Notification', Object.assign(function Notification() {}, { permission, requestPermission }));
}

function renderRow(onPreferenceChange = vi.fn().mockResolvedValue(undefined)) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <DevicePushRow pushEnabled onPreferenceChange={onPreferenceChange} />
    </QueryClientProvider>
  );
  return onPreferenceChange;
}

describe('DevicePushRow', () => {
  beforeEach(() => {
    vi.spyOn(notificationApi, 'getPushPublicKey').mockResolvedValue({ enabled: true, publicKey: KEY });
    vi.spyOn(notificationApi, 'registerPushSubscription').mockResolvedValue();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'serviceWorker');
  });

  it('never asks for permission just because the page opened', async () => {
    installBrowser('default');
    renderRow();

    const toggle = await screen.findByRole('switch', { name: 'Lock-screen notifications' });
    await waitFor(() => expect(toggle).toBeEnabled());
    expect(requestPermission).not.toHaveBeenCalled();
    expect(toggle).not.toBeChecked();
  });

  it('asks for permission only after a tap, then saves the preference', async () => {
    installBrowser('default');
    const onPreferenceChange = renderRow();
    const toggle = await screen.findByRole('switch', { name: 'Lock-screen notifications' });
    await waitFor(() => expect(toggle).toBeEnabled());

    await act(async () => {
      fireEvent.click(toggle);
    });

    expect(requestPermission).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(onPreferenceChange).toHaveBeenCalledWith(true));
    expect(notificationApi.registerPushSubscription).toHaveBeenCalled();
  });

  it('explains how to unblock when notifications were blocked before', async () => {
    installBrowser('denied');
    renderRow();

    expect(await screen.findByText(/blocked for SwiftDrop in this browser/)).toBeInTheDocument();
    expect(requestPermission).not.toHaveBeenCalled();
  });

  it('says when the server has no keys instead of showing a switch that cannot work', async () => {
    installBrowser('default');
    vi.mocked(notificationApi.getPushPublicKey).mockResolvedValue({ enabled: false, publicKey: null });
    renderRow();

    expect(await screen.findByText(/Not available yet/)).toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });
});
