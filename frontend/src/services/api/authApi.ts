import { apiClient } from '@/services/api/apiClient';
import type { components } from '@/types/generated/auth-iam-service';

export type AuthTokenResponse = components['schemas']['TokenResponse'];
export type AuthUserProfile = components['schemas']['UserProfileResponse'];
export type UserRole = NonNullable<AuthUserProfile['role']>;
export type UserStatus = NonNullable<components['schemas']['UserStatus']>;
/** Alias used by admin pages to reference a user list entry. */
export type UserProfileItem = components['schemas']['UserProfileResponse'];

export async function loginUser(username: string, password: string): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/login', { username, password });
  return data;
}

export type RegisterUserInput = {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  district: components['schemas']['SriLankanDistrict'];
};

export const SRI_LANKAN_DISTRICTS: components['schemas']['SriLankanDistrict'][] = [
  'COLOMBO', 'GAMPAHA', 'KALUTARA', 'KANDY', 'MATALE', 'NUWARA_ELIYA', 'GALLE', 'MATARA',
  'HAMBANTOTA', 'JAFFNA', 'KILINOCHCHI', 'MANNAR', 'VAVUNIYA', 'MULLAITIVU', 'BATTICALOA',
  'AMPARA', 'TRINCOMALEE', 'KURUNEGALA', 'PUTTALAM', 'ANURADHAPURA', 'POLONNARUWA', 'BADULLA',
  'MONARAGALA', 'RATNAPURA', 'KEGALLE'
];

/**
 * Self-service registration always creates a CUSTOMER account. The register endpoint returns the
 * new profile (not tokens), so sign the user in straight afterwards to obtain a session.
 */
export async function registerUser(input: RegisterUserInput): Promise<AuthTokenResponse> {
  await apiClient.post('/v1/auth/register', { ...input, role: 'CUSTOMER' });
  return loginUser(input.username, input.password);
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

/**
 * Swaps the one-time OAuth exchange code (from the Google callback redirect) for a full
 * SwiftDrop token pair.  The code expires in 2 minutes and is single-use.
 */
export async function exchangeOAuthCode(code: string): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/oauth2/exchange', { code });
  return data;
}

/**
 * Returns the URL that starts the Google OAuth 2.0 login flow on the backend.
 * The browser is navigated here directly (not via fetch/axios) so Google can
 * perform a full redirect.
 */
export function googleAuthorizeUrl(): string {
  return '/api/v1/auth/oauth2/google/authorize';
}

// ── Admin user management (SYSTEM_ADMIN only) ─────────────────────────────

export type PagedUsers = {
  content: UserProfileItem[];
  totalElements: number;
  totalPages: number;
  page: number;
  hasNext: boolean;
};

/** List / search all platform users. Requires SYSTEM_ADMIN role. */
export async function listUsers(params?: {
  role?: UserRole;
  status?: UserStatus;
  search?: string;
  page?: number;
  size?: number;
}): Promise<PagedUsers> {
  const { data } = await apiClient.get<{ data?: components['schemas']['PagedUserProfileResponse'] }>(
    '/v1/users',
    { params }
  );
  const paged = data.data ?? {};
  return {
    content:       paged.content       ?? [],
    totalElements: paged.totalElements ?? 0,
    totalPages:    paged.totalPages    ?? 0,
    page:          paged.page          ?? 0,
    hasNext:       paged.hasNext       ?? false
  };
}

/** Change a user's role. Requires SYSTEM_ADMIN role. */
export async function updateUserRole(userId: string, role: UserRole): Promise<UserProfileItem> {
  const { data } = await apiClient.patch<{ data?: UserProfileItem }>(
    `/v1/users/${userId}/role`,
    { role }
  );
  return data.data ?? {};
}

/** Change a user's account status. Requires SYSTEM_ADMIN role. */
export async function updateUserStatus(userId: string, status: UserStatus): Promise<UserProfileItem> {
  const { data } = await apiClient.patch<{ data?: UserProfileItem }>(
    `/v1/users/${userId}/status`,
    { status }
  );
  return data.data ?? {};
}

/** Maps the `oauth_error` code from the backend redirect to a user-facing message. */
export function oauthErrorMessage(code: string): string {
  switch (code) {
    case 'access_denied':
      return 'Google sign-in was cancelled. Please try again.';
    case 'invalid_state':
      return 'Your Google sign-in session expired. Please try again.';
    case 'account_inactive':
      return 'Your account is not active. Contact support.';
    case 'not_configured':
      return 'Google sign-in is not available right now.';
    default:
      return 'Google sign-in failed. Please try again.';
  }
}
