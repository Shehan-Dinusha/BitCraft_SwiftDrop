import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Filter,
  LockKeyhole,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck,
  Users,
  UserX
} from 'lucide-react';
import {
  listUsers,
  updateUserStatus,
  type UserProfileItem,
  type UserRole,
  type UserStatus
} from '@/services/api/authApi';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { useAuthStore } from '@/store/useAuthStore';

const ROLE_LABELS: Record<UserRole, string> = {
  CUSTOMER: 'Customer',
  COURIER: 'Courier',
  DISPATCHER: 'Dispatcher',
  MERCHANT: 'Merchant',
  SYSTEM_ADMIN: 'System admin'
};
const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  DEACTIVATED: 'Deactivated'
};
const STATUS_STYLES: Record<UserStatus, string> = {
  ACTIVE: 'bg-status-success-bg text-status-success-fg',
  SUSPENDED: 'bg-status-warning-bg text-status-warning-fg',
  DEACTIVATED: 'bg-status-danger-bg text-status-danger-fg'
};
type AccountActionStatus = 'ACTIVE' | 'SUSPENDED';
const ACTIONS: Record<AccountActionStatus, { label: string; past: string; description: string }> = {
  ACTIVE: {
    label: 'Reactivate',
    past: 'reactivated',
    description:
      'This account will be able to sign in again. The user will need to start a new session.'
  },
  SUSPENDED: {
    label: 'Suspend',
    past: 'suspended',
    description:
      'This account will lose access to SwiftDrop and its active sessions will be revoked. You can reactivate it later.'
  }
};
const PAGE_SIZE = 20;
function errorMessage(error: unknown) {
  return axios.isAxiosError(error)
    ? (error.response?.data?.message ?? 'The request could not be completed. Please try again.')
    : 'Unable to reach the server. Please try again.';
}
export function UserManagementPage() {
  const currentUserId = useAuthStore((state) => state.user?.id);
  const client = useQueryClient();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [role, setRole] = useState<UserRole | ''>('');
  const [status, setStatus] = useState<UserStatus | ''>('');
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState<{ user: UserProfileItem; status: AccountActionStatus }>();
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState('');
  const [success, setSuccess] = useState('');
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(search.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const query = useQuery({
    queryKey: ['admin-users', currentUserId, debouncedSearch, role, status, page],
    queryFn: () =>
      listUsers({
        search: debouncedSearch || undefined,
        role: role || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE
      }),
    retry: false
  });
  const users = query.data?.content ?? [];
  const total = query.data?.totalElements ?? 0;
  const pages = Math.max(1, query.data?.totalPages ?? 0);
  useEffect(() => {
    if (query.data && page >= pages) setPage(pages - 1);
  }, [query.data, page, pages]);
  const filtered = Boolean(search || role || status);
  function resetFilters() {
    setSearch('');
    setRole('');
    setStatus('');
    setPage(0);
  }
  function requestAction(user: UserProfileItem, next: AccountActionStatus) {
    setActionError('');
    setSuccess('');
    setPending({ user, status: next });
  }
  async function confirmAction() {
    if (!pending?.user.id || saving) return;
    setSaving(true);
    setActionError('');
    try {
      await updateUserStatus(pending.user.id, pending.status);
      setSuccess(`${pending.user.username} was ${ACTIONS[pending.status].past} successfully.`);
      setPending(undefined);
      await client.invalidateQueries({ queryKey: ['admin-users'] });
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="mx-auto max-w-[1440px] space-y-6 p-4 md:p-7 lg:p-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-widest text-brand-primary">
            Account administration
          </p>
          <h1 className="text-3xl font-bold tracking-tight">User management</h1>
          <p className="mt-2 text-sm text-secondary">
            Manage account access while keeping assigned roles consistent.
          </p>
        </div>
        <Button
          variant="outline"
          density="compact"
          disabled={query.isFetching || saving}
          leftIcon={<RefreshCw className={query.isFetching ? 'animate-spin' : ''} />}
          onClick={() => void query.refetch()}
        >
          Refresh
        </Button>
      </header>
      <div className="grid gap-4 sm:grid-cols-3">
        <SurfaceCard density="compact" className="rounded-xl shadow-sm">
          <div className="flex items-center gap-3 p-2">
            <span className="rounded-xl bg-brand-subtle p-3 text-brand-primary">
              <Users size={21} />
            </span>
            <div>
              <p className="text-xs text-secondary">
                {filtered ? 'Matching accounts' : 'Platform accounts'}
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {query.data ? total.toLocaleString() : '—'}
              </p>
            </div>
          </div>
        </SurfaceCard>
        <SurfaceCard density="compact" className="rounded-xl shadow-sm">
          <div className="flex items-center gap-3 p-2">
            <span className="rounded-xl bg-status-success-bg p-3 text-status-success-fg">
              <ShieldCheck size={21} />
            </span>
            <div>
              <p className="text-sm font-semibold">Controlled account access</p>
              <p className="mt-1 text-xs text-secondary">Confirm before changing account status</p>
            </div>
          </div>
        </SurfaceCard>
        <SurfaceCard density="compact" className="rounded-xl shadow-sm">
          <div className="flex items-center gap-3 p-2">
            <span className="rounded-xl bg-canvas p-3 text-secondary">
              <LockKeyhole size={21} />
            </span>
            <div>
              <p className="text-sm font-semibold">Roles are protected</p>
              <p className="mt-1 text-xs text-secondary">Assigned at account creation</p>
            </div>
          </div>
        </SurfaceCard>
      </div>
      {success && (
        <div
          role="status"
          className="flex items-center gap-2 rounded-xl border border-status-success-border bg-status-success-bg px-4 py-3 text-sm text-status-success-fg"
        >
          <CheckCircle2 size={18} />
          {success}
        </div>
      )}
      {query.isError && (
        <div
          role="alert"
          className="rounded-xl border border-status-danger-border bg-status-danger-bg px-4 py-3 text-sm text-status-danger-fg"
        >
          {errorMessage(query.error)}{' '}
          <button className="ml-2 underline" onClick={() => void query.refetch()}>
            Retry
          </button>
        </div>
      )}
      <SurfaceCard density="compact" className="rounded-xl shadow-sm">
        <div className="flex flex-wrap items-end gap-3 p-2">
          <label className="flex min-w-48 flex-1 flex-col gap-1.5 text-xs font-medium text-secondary">
            Search accounts
            <span className="flex h-10 items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 focus-within:ring-2 focus-within:ring-border-focus">
              <Search size={17} />
              <input
                type="search"
                aria-label="Search username or email"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search username or email…"
                className="min-w-0 flex-1 bg-transparent text-sm text-primary outline-none placeholder:text-muted"
              />
            </span>
          </label>
          <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
            Role
            <select
              aria-label="Filter by role"
              value={role}
              onChange={(event) => {
                setRole(event.target.value as UserRole | '');
                setPage(0);
              }}
              className="h-10 min-w-36 rounded-lg border border-border-subtle bg-card px-3 text-sm text-primary focus-visible:outline-border-focus"
            >
              <option value="">All roles</option>
              {Object.entries(ROLE_LABELS).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
            Status
            <select
              aria-label="Filter by status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as UserStatus | '');
                setPage(0);
              }}
              className="h-10 min-w-36 rounded-lg border border-border-subtle bg-card px-3 text-sm text-primary focus-visible:outline-border-focus"
            >
              <option value="">All statuses</option>
              {Object.entries(STATUS_LABELS).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          {filtered && (
            <Button variant="ghost" density="compact" onClick={resetFilters}>
              Clear filters
            </Button>
          )}
        </div>
      </SurfaceCard>
      <SurfaceCard density="compact" className="overflow-hidden rounded-xl shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border-subtle px-2 pb-4 pt-2">
          <h2 className="font-semibold">
            Account directory{' '}
            <span className="ml-2 rounded-full bg-canvas px-2.5 py-1 text-xs font-medium text-secondary">
              {total}
            </span>
          </h2>
          <span className="flex items-center gap-1.5 text-xs text-secondary">
            <Filter size={13} />
            {filtered ? 'Filters applied' : 'All accounts'}
          </span>
        </div>
        {query.isPending ? (
          <p role="status" className="py-20 text-center text-sm text-secondary">
            Loading accounts…
          </p>
        ) : !query.isError && users.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-16 text-center">
            <span className="rounded-full bg-canvas p-4 text-muted">
              <Users size={26} />
            </span>
            <p className="font-semibold">No accounts found</p>
            <p className="text-sm text-secondary">Try another search or adjust the filters.</p>
            {filtered && (
              <Button variant="outline" onClick={resetFilters}>
                Clear filters
              </Button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm" aria-busy={query.isFetching}>
              <caption className="sr-only">
                Platform accounts with read-only roles and account status actions
              </caption>
              <thead>
                <tr className="border-b border-border-subtle bg-canvas text-xs text-secondary">
                  {['Account', 'Role', 'Status', 'District', 'Access actions'].map((label) => (
                    <th
                      key={label}
                      scope="col"
                      className={`px-4 py-3 font-medium ${label === 'Access actions' ? 'text-right' : ''}`}
                    >
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {users.map((user) => {
                  const accountStatus = user.status ?? 'ACTIVE';
                  const self = user.id === currentUserId;
                  return (
                    <tr key={user.id} className="transition-colors hover:bg-canvas">
                      <td className="px-4 py-4">
                        <div className="flex items-center gap-3">
                          <span
                            aria-hidden="true"
                            className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-subtle text-sm font-semibold uppercase text-brand-primary"
                          >
                            {user.username?.slice(0, 2) ?? '?'}
                          </span>
                          <div>
                            <p className="font-semibold text-primary">
                              {user.username}
                              {self && (
                                <span className="ml-2 rounded bg-canvas px-1.5 py-0.5 text-[10px] font-medium text-secondary">
                                  You
                                </span>
                              )}
                            </p>
                            <p className="mt-0.5 text-xs text-secondary">{user.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-4">
                        <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border border-border-subtle bg-canvas px-2.5 py-1.5 text-xs font-medium">
                          <LockKeyhole size={12} className="text-muted" />
                          {user.role ? ROLE_LABELS[user.role] : 'Unassigned'}
                        </span>
                      </td>
                      <td className="px-4 py-4">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${STATUS_STYLES[accountStatus]}`}
                        >
                          <span className="size-1.5 rounded-full bg-current" />
                          {STATUS_LABELS[accountStatus]}
                        </span>
                      </td>
                      <td className="px-4 py-4 text-xs text-secondary">
                        {user.district?.replaceAll('_', ' ') ?? '—'}
                      </td>
                      <td className="px-4 py-4">
                        <div className="flex justify-end gap-2">
                          {self ? (
                            <span className="text-xs text-muted">Current session</span>
                          ) : (
                            <>
                              <Button
                                variant="outline"
                                density="compact"
                                disabled={saving || !user.id}
                                leftIcon={accountStatus === 'ACTIVE' ? <UserX /> : <UserCheck />}
                                onClick={() =>
                                  requestAction(
                                    user,
                                    accountStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
                                  )
                                }
                              >
                                {accountStatus === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
                              </Button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <div className="mt-2 flex flex-wrap items-center justify-between gap-3 border-t border-border-subtle px-2 pt-4 text-xs text-secondary">
          <p>
            {total
              ? `${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, total)} of ${total} accounts`
              : '0 accounts'}
          </p>
          <div className="flex items-center gap-3">
            <span>
              Page {page + 1} of {pages}
            </span>
            <Button
              variant="outline"
              density="compact"
              aria-label="Previous page"
              disabled={page === 0 || query.isFetching}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft size={15} />
            </Button>
            <Button
              variant="outline"
              density="compact"
              aria-label="Next page"
              disabled={page >= pages - 1 || query.isFetching}
              onClick={() => setPage(page + 1)}
            >
              <ChevronRight size={15} />
            </Button>
          </div>
        </div>
      </SurfaceCard>
      <Modal
        open={Boolean(pending)}
        size="sm"
        onClose={() => {
          if (!saving) {
            setPending(undefined);
            setActionError('');
          }
        }}
        title={pending ? `${ACTIONS[pending.status].label} account?` : 'Account status'}
        description={pending ? ACTIONS[pending.status].description : undefined}
        footer={
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              density="compact"
              disabled={saving}
              onClick={() => {
                setPending(undefined);
                setActionError('');
              }}
            >
              Cancel
            </Button>
            <Button
              variant={pending?.status === 'ACTIVE' ? 'primary' : 'destructive'}
              density="compact"
              loading={saving}
              loadingLabel="Updating…"
              onClick={() => void confirmAction()}
            >
              {pending ? `${ACTIONS[pending.status].label} account` : 'Confirm'}
            </Button>
          </div>
        }
      >
        {pending && (
          <div className="space-y-3">
            <div className="rounded-lg border border-border-subtle bg-canvas p-3">
              <p className="font-semibold">{pending.user.username}</p>
              <p className="mt-1 text-sm text-secondary">{pending.user.email}</p>
              <p className="mt-2 text-xs text-secondary">
                {pending.user.role ? ROLE_LABELS[pending.user.role] : 'Unassigned'} ·{' '}
                {STATUS_LABELS[pending.user.status ?? 'ACTIVE']} → {STATUS_LABELS[pending.status]}
              </p>
            </div>
            {actionError && (
              <p role="alert" className="text-sm text-status-danger">
                {actionError}
              </p>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
export default UserManagementPage;
