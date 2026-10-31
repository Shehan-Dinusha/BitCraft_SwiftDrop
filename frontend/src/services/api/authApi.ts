import { apiClient } from '@/services/api/apiClient';
import type { components } from '@/types/generated/auth-iam-service';

export type AuthTokenResponse = components['schemas']['TokenResponse'];
export type AuthUserProfile = components['schemas']['UserProfileResponse'];
export type UserRole = NonNullable<AuthUserProfile['role']>;

export async function loginUser(username: string, password: string): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/login', { username, password });
  return data;
}

export async function refreshTokens(refreshToken: string): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/refresh', { refreshToken });
  return data;
}

export async function logoutUser(refreshToken?: string | null): Promise<void> {
  await apiClient.post('/v1/auth/logout', refreshToken ? { refreshToken } : {});
}

export async function fetchCurrentUser(): Promise<AuthUserProfile> {
  const { data } = await apiClient.get<{ data?: AuthUserProfile }>('/v1/users/me');
  return data.data ?? {};
}
