import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, restoreSession } from './apiClient';
import { useAuthStore } from '@/store/useAuthStore';

const FRESH = 'fresh-access-token';

/** Fake transport: answers 401 unless the request carries the fresh token. */
function fakeAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  const auth = String(config.headers.Authorization ?? '');
  const response = { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config };
  if (auth === `Bearer ${FRESH}`) return Promise.resolve(response);
  const unauthorized = { ...response, data: {}, status: 401, statusText: 'Unauthorized' };
  return Promise.reject(new AxiosError('Unauthorized', '401', config, null, unauthorized));
}

function signedInWith(token: string) {
  useAuthStore.setState({
    token,
    user: { id: 'u1', username: 'alice', email: 'alice@example.com', role: 'CUSTOMER' },
    role: 'CUSTOMER',
    isDevSession: false
  });
}

describe('apiClient session refresh', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    apiClient.defaults.adapter = fakeAdapter;
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    useAuthStore.getState().signOut();
    vi.restoreAllMocks();
  });

  it('refreshes once with the cookie and retries every request that got a 401', async () => {
    signedInWith('stale-access-token');
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: FRESH, userId: 'u1', username: 'alice', role: 'CUSTOMER' }
    });

    const results = await Promise.all([apiClient.get('/a'), apiClient.get('/b'), apiClient.get('/c')]);

    expect(results.every((r) => r.status === 200)).toBe(true);
    expect(refresh).toHaveBeenCalledTimes(1);
    // No refresh token in the body: the browser sends the HttpOnly cookie.
    expect(refresh).toHaveBeenCalledWith('/api/v1/auth/refresh', {}, { withCredentials: true });
    expect(useAuthStore.getState().token).toBe(FRESH);
  });

  it('signs the user out when the refresh is rejected', async () => {
    signedInWith('stale-access-token');
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh token revoked'));

    await expect(apiClient.get('/a')).rejects.toBeInstanceOf(AxiosError);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('does not try to refresh when nobody is signed in', async () => {
    const refresh = vi.spyOn(axios, 'post');

    await expect(apiClient.get('/a')).rejects.toBeInstanceOf(AxiosError);
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe('restoreSession (a new tab)', () => {
  beforeEach(() => {
    // A new tab: nothing in sessionStorage, and no attempt made yet.
    useAuthStore.setState({ token: null, user: null, isDevSession: false, sessionRestore: 'idle' });
  });

  afterEach(() => {
    useAuthStore.getState().signOut();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('signs the tab in from the shared refresh cookie', async () => {
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: FRESH, userId: 'u1', username: 'alice', role: 'COURIER' }
    });

    await restoreSession();

    expect(refresh).toHaveBeenCalledWith('/api/v1/auth/refresh', {}, { withCredentials: true });
    expect(useAuthStore.getState().token).toBe(FRESH);
    expect(useAuthStore.getState().role).toBe('COURIER');
    expect(useAuthStore.getState().sessionRestore).toBe('done');
  });

  it('stays signed out without a valid cookie, and tries only once', async () => {
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(new Error('no cookie'));

    await restoreSession();
    await restoreSession();

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().sessionRestore).toBe('done');
  });

  it('refreshes under a lock shared by every tab, so two tabs never spend the same cookie', async () => {
    const request = vi.fn((_name: string, callback: () => Promise<unknown>) => callback());
    vi.stubGlobal('navigator', { ...navigator, locks: { request } });
    vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: FRESH, userId: 'u1', username: 'alice', role: 'CUSTOMER' }
    });

    await restoreSession();

    expect(request).toHaveBeenCalledWith('swiftdrop-auth-refresh', expect.any(Function));
    expect(useAuthStore.getState().token).toBe(FRESH);
  });

  it('does not sign back in after the user signed out in this tab', async () => {
    const refresh = vi.spyOn(axios, 'post');
    useAuthStore.getState().signOut();

    await restoreSession();

    expect(refresh).not.toHaveBeenCalled();
  });
});

describe('another account signed in in another tab', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    apiClient.defaults.adapter = fakeAdapter;
    // This tab is the courier; another tab then signed in as a customer, replacing the browser's cookie.
    useAuthStore.setState({
      token: 'courier-expired-token',
      user: { id: 'courier-1', username: 'kavindi', role: 'COURIER' },
      role: 'COURIER',
      isDevSession: false,
      accountChanged: false
    });
    window.localStorage.setItem('swiftdrop-signed-in', '1');
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    useAuthStore.getState().signOut();
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('ends this tab instead of turning the courier into the customer', async () => {
    vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: FRESH, userId: 'customer-1', username: 'nimal', role: 'CUSTOMER' }
    });

    await expect(apiClient.get('/courier/jobs')).rejects.toBeInstanceOf(AxiosError);

    const state = useAuthStore.getState();
    expect(state.token).toBeNull();
    expect(state.user).toBeNull();
    expect(state.accountChanged).toBe(true);
    // The customer is still signed in: their flag stays for other tabs.
    expect(window.localStorage.getItem('swiftdrop-signed-in')).toBe('1');
  });

  it('keeps the tab signed in when the cookie still belongs to the same user', async () => {
    vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: FRESH, userId: 'courier-1', username: 'kavindi', role: 'COURIER' }
    });

    const response = await apiClient.get('/courier/jobs');

    expect(response.status).toBe(200);
    expect(useAuthStore.getState().token).toBe(FRESH);
    expect(useAuthStore.getState().accountChanged).toBe(false);
  });
});
