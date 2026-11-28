import { ReactNode, useEffect } from 'react';
import { restoreSession } from '@/services/api/apiClient';
import { hasSignedInHint } from '@/store/useAuthStore';

/**
 * A tab opened by hand starts without an access token (it lives in sessionStorage). When this browser has
 * signed in, sign the tab in from the shared refresh cookie straight away, so public pages such as the home
 * page show the signed-in view too. Protected pages also do this through RoleGuard.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (hasSignedInHint()) void restoreSession();
  }, []);
  return <>{children}</>;
}
