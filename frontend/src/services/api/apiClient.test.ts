import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './apiClient';
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
