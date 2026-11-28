import axios from 'axios';
import { render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { hasSignedInHint, useAuthStore } from '@/store/useAuthStore';

const SIGNED_IN = {
  accessToken: 'from-cookie',
  userId: 'u1',
  username: 'kavindi',
  role: 'COURIER'
};

describe('AuthProvider (a new tab on a public page)', () => {
  beforeEach(() => {
    // A new tab: no token in this tab, no restore tried yet.
    useAuthStore.setState({ token: null, user: null, isDevSession: false, sessionRestore: 'idle' });
  });

  afterEach(() => {
    useAuthStore.getState().signOut();
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('signs the tab in from the cookie when this browser has signed in', async () => {
    window.localStorage.setItem('swiftdrop-signed-in', '1');
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({ data: SIGNED_IN });

    render(<AuthProvider>home page</AuthProvider>);

    await waitFor(() => expect(useAuthStore.getState().token).toBe('from-cookie'));
    expect(refresh).toHaveBeenCalledWith('/api/v1/auth/refresh', {}, { withCredentials: true });
  });

  it('sends no request for a visitor who never signed in', () => {
    const refresh = vi.spyOn(axios, 'post');

    render(<AuthProvider>home page</AuthProvider>);

    expect(refresh).not.toHaveBeenCalled();
    expect(useAuthStore.getState().sessionRestore).toBe('idle');
  });

  it('forgets the browser was signed in when the cookie no longer works', async () => {
    window.localStorage.setItem('swiftdrop-signed-in', '1');
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('cookie expired'));

    render(<AuthProvider>home page</AuthProvider>);

    await waitFor(() => expect(useAuthStore.getState().sessionRestore).toBe('done'));
    expect(useAuthStore.getState().token).toBeNull();
    expect(hasSignedInHint()).toBe(false);
  });
});

describe('signed-in hint', () => {
  afterEach(() => window.localStorage.clear());

  it('is set on sign-in and cleared on sign-out, and holds no token', () => {
    useAuthStore.getState().setAuth(SIGNED_IN as never);
    expect(window.localStorage.getItem('swiftdrop-signed-in')).toBe('1');
    expect(JSON.stringify(window.localStorage)).not.toContain('from-cookie');

    useAuthStore.getState().signOut();
    expect(hasSignedInHint()).toBe(false);
  });
});
