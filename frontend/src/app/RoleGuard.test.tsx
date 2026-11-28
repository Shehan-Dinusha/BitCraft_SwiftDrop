import axios from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RoleGuard } from './RoleGuard';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<p>login page</p>} />
        <Route path="/customer" element={<p>customer home</p>} />
        <Route
          path="/ops"
          element={
            <RoleGuard roles={['ADMIN_OPS']}>
              <p>ops console</p>
            </RoleGuard>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

function signInAs(role: PersonaRole) {
  useAuthStore.setState({ token: 'access-token', role, isDevSession: false });
}

describe('RoleGuard', () => {
  afterEach(() => {
    useAuthStore.getState().signOut();
    vi.restoreAllMocks();
  });

  it('sends a visitor without a token to the login page', async () => {
    useAuthStore.setState({ sessionRestore: 'idle' });
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('no refresh cookie'));
    renderAt('/ops');
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('signs a new tab in from the refresh cookie instead of asking to sign in again', async () => {
    useAuthStore.setState({ sessionRestore: 'idle' });
    vi.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: 'from-cookie', userId: 'u1', username: 'ops', role: 'DISPATCHER' }
    });
    renderAt('/ops');
    expect(screen.getByRole('status')).toHaveTextContent('Signing you in');
    expect(await screen.findByText('ops console')).toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });

  it('renders the page for an allowed persona', () => {
    signInAs('ADMIN_OPS');
    renderAt('/ops');
    expect(screen.getByText('ops console')).toBeInTheDocument();
  });

  it('sends a signed-in user with another persona to their own home, not to login', () => {
    signInAs('CUSTOMER');
    renderAt('/ops');
    expect(screen.getByText('customer home')).toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });
});
