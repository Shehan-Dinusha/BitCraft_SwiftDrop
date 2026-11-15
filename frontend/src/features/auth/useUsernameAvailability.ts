import { useEffect, useState } from 'react';
import axios from 'axios';
import { checkUsernameAvailability } from '@/services/api/authApi';

export type UsernameStatus = 'idle' | 'invalid' | 'checking' | 'available' | 'taken' | 'unknown';

/** Same rule as RegisterUserRequest.username on the server. */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,50}$/;

const DEBOUNCE_MS = 400;

/**
 * Asks the API whether a username is free, 400 ms after the user stops typing. Each new keystroke
 * cancels the request in flight, so a slow answer for an old value can never overwrite a newer one.
 * `unknown` means the check itself failed (offline, rate limited): the form still submits and the
 * server decides.
 */
export function useUsernameAvailability(rawUsername: string): UsernameStatus {
  const username = rawUsername.trim();
  const [result, setResult] = useState<{ username: string; status: UsernameStatus }>({
    username: '',
    status: 'idle'
  });

  const isValid = USERNAME_PATTERN.test(username);

  useEffect(() => {
    if (!isValid) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      checkUsernameAvailability(username, controller.signal)
        .then(({ available, reason }) =>
          setResult({
            username,
            status: available ? 'available' : reason === 'INVALID' ? 'invalid' : 'taken'
          })
        )
        .catch((error: unknown) => {
          if (!axios.isCancel(error)) setResult({ username, status: 'unknown' });
        });
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [username, isValid]);

  if (!username) return 'idle';
  if (!isValid) return 'invalid';
  // Until the answer for *this* value arrives, show "checking" rather than a stale result.
  return result.username === username ? result.status : 'checking';
}
