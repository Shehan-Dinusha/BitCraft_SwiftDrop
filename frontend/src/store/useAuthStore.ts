import { create } from 'zustand';
import { clearAllDrafts } from '@/features/customer/components/booking/bookingDraft';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { PersonaRole } from '@/types/contracts';
import type { AuthTokenResponse, AuthUserProfile, UserRole } from '@/services/api/authApi';

/**
 * Maps a backend UserRole to the frontend PersonaRole (persona).
 *
 * Mapping:
 *   CUSTOMER    → CUSTOMER
 *   COURIER     → COURIER
 *   MERCHANT    → MERCHANT
 *   DISPATCHER  → ADMIN_OPS   (operational console; district-scoped)
 *   SYSTEM_ADMIN→ ADMIN_OPS   (platform-wide; AppShell uses user.role to show the right sub-nav)
 *   <unknown>   → CUSTOMER    (safe default; RoleGuard blocks access to protected routes)
 */
export function personaForRole(role?: UserRole | AuthUserProfile['role']): PersonaRole {
  switch (role) {
    case 'COURIER':
      return 'COURIER';
    case 'MERCHANT':
      return 'MERCHANT';
    case 'DISPATCHER':
    case 'SYSTEM_ADMIN':
      // Both map to ADMIN_OPS persona.  Use `user.role` (the raw backend value) in ADMIN_OPS
      // screens to distinguish dispatcher vs. system-admin capabilities.
      return 'ADMIN_OPS';
    default:
      return 'CUSTOMER';
  }
}

// Shared by every tab (localStorage), unlike the token: says only that this browser has signed in, so a new
// tab on a public page (the home page, sign-in, tracking) knows the refresh cookie is worth trying. Holds no
// secret. Anonymous visitors never have it, so they send no refresh request.
const SIGNED_IN_HINT = 'swiftdrop-signed-in';

export function hasSignedInHint(): boolean {
  try {
    return window.localStorage.getItem(SIGNED_IN_HINT) === '1';
  } catch {
    return false;
  }
}

export function setSignedInHint(signedIn: boolean): void {
  try {
    if (signedIn) window.localStorage.setItem(SIGNED_IN_HINT, '1');
    else window.localStorage.removeItem(SIGNED_IN_HINT);
  } catch {
    // Storage blocked (private window): new tabs then sign in from protected pages only.
  }
}

/**
 * Browser session. Only the short-lived access token is kept here; the refresh token is an
 * HttpOnly cookie set by the auth service, so no script (and no XSS payload) can read it.
 */
type Session = {
  token: string | null;
  user: AuthUserProfile | null;
  role: PersonaRole;
  /** True for a developer-switched persona with a fake token. The API client leaves it signed in on 401. */
  isDevSession: boolean;
  /**
   * Whether this tab has tried to restore the session from the refresh cookie. The access token lives in
   * sessionStorage, which a new tab starts without, but the cookie is shared by every tab. Not persisted.
   */
  sessionRestore: 'idle' | 'running' | 'done';
  setSessionRestore: (state: 'idle' | 'running' | 'done') => void;
  setSession: (token: string, role: PersonaRole) => void;
  setAuth: (tokens: AuthTokenResponse) => void;
  setUser: (user: AuthUserProfile) => void;
  signOut: () => void;
};

export const useAuthStore = create<Session>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      role: 'CUSTOMER',
      isDevSession: false,
      sessionRestore: 'idle',
      setSessionRestore: (sessionRestore) => set({ sessionRestore }),
      setSession: (token, role) => set({ token, role, user: null, isDevSession: true }),
      setAuth: (tokens) => {
        set({
          token: tokens.accessToken ?? null,
          user: {
            id: tokens.userId,
            username: tokens.username,
            email: tokens.email,
            role: tokens.role,
            district: tokens.district
          },
          role: personaForRole(tokens.role),
          isDevSession: false
        });
        setSignedInHint(true);
      },
      setUser: (user) => set({ user, role: personaForRole(user.role), isDevSession: false }),
      signOut: () => {
        // A shared phone must not show the previous person's unfinished booking (addresses, phones).
        clearAllDrafts();
        setSignedInHint(false);
        set({
          token: null,
          user: null,
          role: 'CUSTOMER',
          isDevSession: false,
          // Signed out on purpose (or the session ended): do not sign straight back in from the cookie.
          sessionRestore: 'done'
        });
      }
    }),
    {
      name: 'swiftdrop-auth',
      storage: createJSONStorage(() => sessionStorage),
      partialize: ({ token, user, role, isDevSession }) => ({
        token,
        user,
        role,
        isDevSession
      })
    }
  )
);
