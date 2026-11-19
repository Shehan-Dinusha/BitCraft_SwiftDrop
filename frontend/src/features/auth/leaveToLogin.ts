import type { NavigateFunction } from 'react-router-dom';

/**
 * Ends the local session and shows the sign-in page with a short message ("You have been signed
 * out", "Password changed", ...).
 *
 * The message has to be sent after the session is cleared: the moment the token goes, the route
 * guard redirects to /login with state of its own, and that redirect would overwrite a message sent
 * in the same breath. Waiting one tick lets the guard finish first, so the message is what the
 * sign-in page ends up with.
 */
export function leaveToLogin(signOut: () => void, navigate: NavigateFunction, notice: string) {
  signOut();
  window.setTimeout(() => navigate('/login', { replace: true, state: { notice } }), 0);
}

export default leaveToLogin;
