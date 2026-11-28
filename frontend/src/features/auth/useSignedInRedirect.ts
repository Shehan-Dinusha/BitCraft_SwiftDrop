import { useState } from 'react';
import { homeFor } from '@/app/roleHome';
import { useAuthStore } from '@/store/useAuthStore';

/**
 * For the sign-in and sign-up pages: when the visitor ALREADY has a real session on arrival, returns
 * their home route so the page can redirect there. Evaluated once on mount, so signing in on the page
 * itself keeps its own redirect (including the original `from` page). Dev-persona sessions are ignored.
 */
export function useSignedInRedirect(): string | null {
  const [target] = useState(() => {
    const { token, isDevSession, role, user } = useAuthStore.getState();
    return token && !isDevSession ? homeFor(role, user?.role) : null;
  });
  return target;
}
