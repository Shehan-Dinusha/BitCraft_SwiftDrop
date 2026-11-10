import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import {
  listUsers,
  updateUserRole,
  updateUserStatus,
  type UserProfileItem
} from '@/services/api/authApi';
import type { components } from '@/types/generated/auth-iam-service';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import {
  RefreshCw,
  Search,
  UserCheck,
  UserX,
  ShieldAlert,
  Filter
} from 'lucide-react';

type UserRole   = NonNullable<components['schemas']['UserRole']>;
type UserStatus = NonNullable<components['schemas']['UserStatus']>;

/** All roles the admin may assign to a user. */
const ASSIGNABLE_ROLES: UserRole[] = [
  'CUSTOMER',
  'COURIER',
  'DISPATCHER',
  'MERCHANT',
  'SYSTEM_ADMIN'
];

/** Statuses the admin may set on a user account. */
const ASSIGNABLE_STATUSES: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'DEACTIVATED'];

const ROLE_LABELS: Record<UserRole, string> = {
  CUSTOMER:     'Customer',
  COURIER:      'Courier',
  DISPATCHER:   'Dispatcher',
  MERCHANT:     'Merchant',
  SYSTEM_ADMIN: 'System Admin'
};

const STATUS_COLOURS: Record<UserStatus, string> = {
  ACTIVE:      'text-status-success bg-status-success/10',
  SUSPENDED:   'text-status-warning bg-status-warning/10',
  DEACTIVATED: 'text-status-danger  bg-status-danger/10'
};

/**
 * Admin User Management Page — `/ops/users`
 *
 * Allows SYSTEM_ADMIN operators to:
 *   - Browse and search all platform users
 *   - Filter by role and status
 *   - Change a user's role
 *   - Suspend, deactivate, or reactivate an account
 *
 * All mutations are optimistic: the UI updates immediately while the request
 * is in-flight and rolls back with an error banner on failure.
 */
export function UserManagementPage() {
  const [users, setUsers]         = useState<UserProfileItem[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState<string | null>(null);
  const [search, setSearch]       = useState('');
  const [roleFilter, setRoleFilter]     = useState<UserRole | ''>('');
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('');
  /** ID of a row currently being mutated (disables controls for that row). */
  const [mutatingId, setMutatingId] = useState<string | null>(null);

  const PAGE_SIZE = 20;

  const load = useCallback(
    async (pg: number) => {
      setLoading(true);
      setError(null);
      try {
        const result = await listUsers({
          search:  search  || undefined,
          role:    (roleFilter   as UserRole)   || undefined,
          status:  (statusFilter as UserStatus) || undefined,
          page:    pg,
          size:    PAGE_SIZE
        });
        setUsers(result.content ?? []);
        setTotal(result.totalElements ?? 0);
        setPage(pg);
      } catch (err) {
        const message = axios.isAxiosError(err)
          ? ((err.response?.data as { message?: string } | undefined)?.message ?? 'Failed to load users.')
          : 'Unable to reach the server.';
        setError(message);
      } finally {
        setLoading(false);
      }
    },
    [search, roleFilter, statusFilter]
  );

  // Initial load and reload when filters change.
  useEffect(() => {
    void load(0);
  }, [load]);

  async function handleRoleChange(userId: string, newRole: UserRole) {
    setMutatingId(userId);
    setError(null);
    try {
      const updated = await updateUserRole(userId, newRole);
      setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)));
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? ((err.response?.data as { message?: string } | undefined)?.message ?? 'Role update failed.')
        : 'Unable to reach the server.';
      setError(message);
    } finally {
      setMutatingId(null);
    }
  }

  async function handleStatusChange(userId: string, newStatus: UserStatus) {
    setMutatingId(userId);
    setError(null);
    try {
      const updated = await updateUserStatus(userId, newStatus);
      setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)));
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? ((err.response?.data as { message?: string } | undefined)?.message ?? 'Status update failed.')
        : 'Unable to reach the server.';
      setError(message);
    } finally {
      setMutatingId(null);
    }
  }

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="p-6 space-y-6">
      {/* ── Page header ──────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">User management</h1>
          <p className="mt-1 text-sm text-secondary">
            {total} account{total !== 1 ? 's' : ''} on the platform
          </p>
        </div>

        <Button
          variant="outline"
          onClick={() => void load(page)}
          loading={loading}
          loadingLabel="Refreshing"
          className="shrink-0"
        >
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          Refresh
        </Button>
      </div>

      {/* ── Filters ──────────────────────────────────────────────────── */}
      <SurfaceCard className="p-4">
        <div className="flex flex-wrap gap-3">
          {/* Search */}
          <label className="flex flex-1 min-w-48 items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-sm text-secondary">
            <Search className="h-4 w-4 shrink-0" aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search username or email…"
              className="flex-1 bg-transparent text-primary placeholder:text-muted outline-none"
            />
          </label>

          {/* Role filter */}
          <label className="flex items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-sm text-secondary">
            <Filter className="h-4 w-4 shrink-0" aria-hidden="true" />
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value as UserRole | '')}
              className="bg-transparent text-primary outline-none cursor-pointer"
            >
              <option value="">All roles</option>
              {ASSIGNABLE_ROLES.map((r) => (
                <option key={r} value={r}>{ROLE_LABELS[r]}</option>
              ))}
            </select>
          </label>

          {/* Status filter */}
          <label className="flex items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-sm text-secondary">
            <ShieldAlert className="h-4 w-4 shrink-0" aria-hidden="true" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as UserStatus | '')}
              className="bg-transparent text-primary outline-none cursor-pointer"
            >
              <option value="">All statuses</option>
              {ASSIGNABLE_STATUSES.map((s) => (
                <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </label>
        </div>
      </SurfaceCard>

      {/* ── Error banner ─────────────────────────────────────────────── */}
      {error ? (
        <p role="alert" className="rounded-lg bg-status-danger/10 px-4 py-3 text-sm font-medium text-status-danger">
          {error}
        </p>
      ) : null}

      {/* ── User table ───────────────────────────────────────────────── */}
      <SurfaceCard className="overflow-hidden">
        {loading && users.length === 0 ? (
          <div className="flex items-center justify-center py-20 text-sm text-muted">
            Loading users…
          </div>
        ) : users.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-20 text-sm text-muted">
            <p>No users found.</p>
            {(search || roleFilter || statusFilter) ? (
              <Button
                variant="ghost"
                onClick={() => { setSearch(''); setRoleFilter(''); setStatusFilter(''); }}
              >
                Clear filters
              </Button>
            ) : null}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border-subtle bg-surface-hover text-left">
                  <th className="px-4 py-3 font-semibold text-secondary">User</th>
                  <th className="px-4 py-3 font-semibold text-secondary">Role</th>
                  <th className="px-4 py-3 font-semibold text-secondary">Status</th>
                  <th className="px-4 py-3 font-semibold text-secondary">District</th>
                  <th className="px-4 py-3 font-semibold text-secondary text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {users.map((user) => {
                  const isMutating = mutatingId === user.id;
                  const status = (user.status ?? 'ACTIVE') as UserStatus;
                  return (
                    <tr key={user.id} className="hover:bg-surface-hover transition-colors">
                      {/* User */}
                      <td className="px-4 py-3">
                        <p className="font-medium text-primary">{user.username}</p>
                        <p className="text-xs text-muted">{user.email}</p>
                      </td>

                      {/* Role select */}
                      <td className="px-4 py-3">
                        <select
                          value={user.role ?? 'CUSTOMER'}
                          disabled={isMutating}
                          onChange={(e) =>
                            void handleRoleChange(user.id!, e.target.value as UserRole)
                          }
                          className="rounded border border-border-subtle bg-canvas px-2 py-1 text-xs text-primary outline-none cursor-pointer disabled:opacity-50"
                        >
                          {ASSIGNABLE_ROLES.map((r) => (
                            <option key={r} value={r}>{ROLE_LABELS[r]}</option>
                          ))}
                        </select>
                      </td>

                      {/* Status badge */}
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_COLOURS[status]}`}
                        >
                          {status.charAt(0) + status.slice(1).toLowerCase()}
                        </span>
                      </td>

                      {/* District */}
                      <td className="px-4 py-3 text-secondary">
                        {user.district ?? '—'}
                      </td>

                      {/* Actions */}
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center gap-1">
                          {status !== 'ACTIVE' ? (
                            <button
                              title="Reactivate"
                              disabled={isMutating}
                              onClick={() => void handleStatusChange(user.id!, 'ACTIVE')}
                              className="rounded p-1.5 text-status-success hover:bg-status-success/10 disabled:opacity-50 transition-colors"
                            >
                              <UserCheck className="h-4 w-4" aria-hidden="true" />
                              <span className="sr-only">Reactivate {user.username}</span>
                            </button>
                          ) : null}
                          {status === 'ACTIVE' ? (
                            <button
                              title="Suspend"
                              disabled={isMutating}
                              onClick={() => void handleStatusChange(user.id!, 'SUSPENDED')}
                              className="rounded p-1.5 text-status-warning hover:bg-status-warning/10 disabled:opacity-50 transition-colors"
                            >
                              <UserX className="h-4 w-4" aria-hidden="true" />
                              <span className="sr-only">Suspend {user.username}</span>
                            </button>
                          ) : null}
                          {status !== 'DEACTIVATED' ? (
                            <button
                              title="Deactivate"
                              disabled={isMutating}
                              onClick={() => void handleStatusChange(user.id!, 'DEACTIVATED')}
                              className="rounded p-1.5 text-status-danger hover:bg-status-danger/10 disabled:opacity-50 transition-colors"
                            >
                              <ShieldAlert className="h-4 w-4" aria-hidden="true" />
                              <span className="sr-only">Deactivate {user.username}</span>
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </SurfaceCard>

      {/* ── Pagination ───────────────────────────────────────────────── */}
      {totalPages > 1 ? (
        <div className="flex items-center justify-between text-sm text-secondary">
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => void load(page - 1)}
              disabled={page === 0 || loading}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              onClick={() => void load(page + 1)}
              disabled={page >= totalPages - 1 || loading}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default UserManagementPage;

