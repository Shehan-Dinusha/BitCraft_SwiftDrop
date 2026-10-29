import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { PersonaRole } from '@/types/contracts';
import type { AuthTokenResponse, AuthUserProfile, UserRole } from '@/services/api/authApi';

export function personaForRole(role?: UserRole | AuthUserProfile['role']): PersonaRole {
  switch (role) {
    case 'COURIER':
      return 'COURIER';
    case 'MERCHANT':
      return 'MERCHANT';
    case 'DISPATCHER':
    case 'SYSTEM_ADMIN':
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
        set({ token, role, refreshToken: null, user: null, isDevSession: true }),
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
