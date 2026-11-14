import { create } from 'zustand';
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

type Session = {
  token: string | null;
  refreshToken: string | null;
  user: AuthUserProfile | null;
  role: PersonaRole;
  /** True for a developer-switched persona with a fake token. The API client leaves it signed in on 401. */
  isDevSession: boolean;
  setSession: (token: string, role: PersonaRole) => void;
  setAuth: (tokens: AuthTokenResponse) => void;
  setUser: (user: AuthUserProfile) => void;
  signOut: () => void;
};

export const useAuthStore = create<Session>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      user: null,
      role: 'CUSTOMER',
      isDevSession: false,
      setSession: (token, role) =>
        set({
          token,
          role,
          refreshToken: null,
          user: {
            id: '018f6c32-b7e1-7d34-9128-498c257b4201',
            username: `dev-${role.toLowerCase()}`,
            email: `${role.toLowerCase()}@swiftdrop.lk`,
            role: role === 'ADMIN_OPS' ? 'DISPATCHER' : (role as UserRole),
            district: 'COLOMBO'
          },
          isDevSession: true
        }),
      setAuth: (tokens) =>
        set({
          token: tokens.accessToken ?? null,
          refreshToken: tokens.refreshToken ?? null,
          user: {
            id: tokens.userId,
            username: tokens.username,
            email: tokens.email,
            role: tokens.role,
            district: tokens.district
          },
          role: personaForRole(tokens.role),
          isDevSession: false
        }),
      setUser: (user) => set({ user, role: personaForRole(user.role), isDevSession: false }),
      signOut: () =>
        set({
          token: null,
          refreshToken: null,
          user: null,
          role: 'CUSTOMER',
          isDevSession: false
        })
    }),
    {
      name: 'swiftdrop-auth',
      storage: createJSONStorage(() => sessionStorage),
      partialize: ({ token, refreshToken, user, role, isDevSession }) => ({
        token,
        refreshToken,
        user,
        role,
        isDevSession
      })
    }
  )
);
