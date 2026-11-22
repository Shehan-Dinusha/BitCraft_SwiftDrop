import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminOnly, RoleGuard } from './RoleGuard';
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
        <Route
          path="/ops/admin"
          element={
            <RoleGuard roles={['ADMIN_OPS']}>
              <AdminOnly>
                <p>admin page</p>
              </AdminOnly>
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
  afterEach(() => useAuthStore.getState().signOut());

  it('sends a visitor without a token to the login page', () => {
    renderAt('/ops');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('renders the page for an allowed persona', () => {
    signInAs('ADMIN_OPS');
    renderAt('/ops');
    expect(screen.getByText('ops console')).toBeInTheDocument();
  });

  it('keeps a dispatcher out of an admin page', () => {
    useAuthStore.setState({ token: 'access-token', role: 'ADMIN_OPS', user: { role: 'DISPATCHER' } });
    renderAt('/ops/admin');
    expect(screen.getByText('ops console')).toBeInTheDocument();
  });

  it('lets a system admin into an admin page', () => {
    useAuthStore.setState({ token: 'access-token', role: 'ADMIN_OPS', user: { role: 'SYSTEM_ADMIN' } });
    renderAt('/ops/admin');
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });

  it('sends a signed-in user with another persona to their own home, not to login', () => {
    signInAs('CUSTOMER');
    renderAt('/ops');
    expect(screen.getByText('customer home')).toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });
});
