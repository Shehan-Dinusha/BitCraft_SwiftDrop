import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/useAuthStore';
import type { AuthTokenResponse } from '@/services/api/authApi';

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

export const apiClient = axios.create({ baseURL: '/api' });

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined;
    const isAuthAttempt =
      request?.url?.includes('/v1/auth/login') ||
      request?.url?.includes('/v1/auth/register') ||
      request?.url?.includes('/v1/auth/refresh');

    if (error.response?.status !== 401 || !request || request._retry || isAuthAttempt) {
      return Promise.reject(error);
    }

    // A dev-switched persona has a fake token, so every real call is a 401. Let the page show its own
    // error state instead of signing the developer out and bouncing them to /login.
    if (useAuthStore.getState().isDevSession) {
      return Promise.reject(error);
    }

    const refreshToken = useAuthStore.getState().refreshToken;
    if (!refreshToken) {
      useAuthStore.getState().signOut();
      return Promise.reject(error);
    }

    request._retry = true;
    try {
      const { data } = await axios.post<AuthTokenResponse>('/api/v1/auth/refresh', {
        refreshToken
      });
      useAuthStore.getState().setAuth(data);
      request.headers.Authorization = `Bearer ${data.accessToken ?? ''}`;
      return apiClient(request);
    } catch (refreshError) {
      useAuthStore.getState().signOut();
      return Promise.reject(refreshError);
    }
  }
);
