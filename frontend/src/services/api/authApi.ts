import { apiClient } from '@/services/api/apiClient';
import type { components } from '@/types/generated/auth-iam-service';

export type AuthTokenResponse = components['schemas']['TokenResponse'];
export type AuthUserProfile = components['schemas']['UserProfileResponse'];
export type UserRole = NonNullable<AuthUserProfile['role']>;
export type UserStatus = NonNullable<components['schemas']['UserStatus']>;
/** Alias used by admin pages to reference a user list entry. */
export type UserProfileItem = components['schemas']['UserProfileResponse'];

export type MfaChallenge = components['schemas']['MfaChallengeResponse'];

/** Either a signed-in session, or (admins and dispatchers with MFA on) a second-factor challenge. */
export type LoginResult = AuthTokenResponse | MfaChallenge;

export function isMfaChallenge(result: LoginResult): result is MfaChallenge {
  return (result as MfaChallenge).mfaRequired === true;
}

export async function loginUser(username: string, password: string): Promise<LoginResult> {
  const { data } = await apiClient.post<LoginResult>('/v1/auth/login', { username, password });
  return data;
}

/** Second step of an MFA sign-in: the emailed 6-digit code for the challenge from {@link loginUser}. */
export async function completeMfaLogin(challengeId: string, code: string): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/login/mfa', { challengeId, code });
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
 * Self-service registration always creates a CUSTOMER account. The account starts unverified: the
 * API emails a 6-digit code that {@link verifyEmail} confirms. Registration never signs the user in.
 */
export async function registerUser(input: RegisterUserInput): Promise<void> {
  await apiClient.post('/v1/auth/register', { ...input, role: 'CUSTOMER' });
}

export type UsernameAvailability = components['schemas']['UsernameAvailabilityResponse'];

/** Live check for the sign-up form. The server's unique index stays the authority (register answers 409). */
export async function checkUsernameAvailability(
  username: string,
  signal?: AbortSignal
): Promise<UsernameAvailability> {
  const { data } = await apiClient.get<components['schemas']['ApiResponseUsernameAvailability']>(
    '/v1/auth/username-availability',
    { params: { username }, signal }
  );
  return data.data ?? { username, available: false, reason: null };
}

/** Confirms the emailed code. No session is created: the user signs in afterwards. */
export async function verifyEmail(identifier: string, code: string): Promise<void> {
  await apiClient.post('/v1/auth/verify-email', { identifier, code });
}

/** Asks for a new verification code. The API answers 200 whether or not the account exists. */
export async function resendVerification(identifier: string): Promise<void> {
  await apiClient.post('/v1/auth/resend-verification', { identifier });
}

/** Asks for a password-reset code. The API answers 200 whether or not the account exists. */
export async function forgotPassword(identifier: string): Promise<void> {
  await apiClient.post('/v1/auth/forgot-password', { identifier });
}

/** Sets a new password using the emailed reset code. */
export async function resetPassword(identifier: string, code: string, newPassword: string): Promise<void> {
  await apiClient.post('/v1/auth/reset-password', { identifier, code, newPassword });
}

/** Rotates the session. The refresh token is the HttpOnly cookie, so nothing is sent in the body. */
export async function refreshTokens(): Promise<AuthTokenResponse> {
  const { data } = await apiClient.post<AuthTokenResponse>('/v1/auth/refresh', {});
  return data;
}

/** Revokes the refresh-token cookie's session and blacklists the current access token. */
export async function logoutUser(): Promise<void> {
  await apiClient.post('/v1/auth/logout', {});
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

/** Compatibility role check. Different roles return 409; roles are fixed at creation. */
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
    case 'email_unverified':
      return 'Your Google email address is not verified, so it cannot be used to sign in.';
    case 'account_inactive':
      return 'Your account is not active. Contact support.';
    case 'not_configured':
      return 'Google sign-in is not available right now.';
    default:
      return 'Google sign-in failed. Please try again.';
  }
}
