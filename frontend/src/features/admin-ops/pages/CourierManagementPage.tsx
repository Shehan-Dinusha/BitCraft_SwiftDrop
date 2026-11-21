import { useMemo, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Bike,
  CheckCircle2,
  Mail,
  Pencil,
  RefreshCw,
  Search,
  UserCheck,
  UserPlus,
  Users,
  UserX
} from 'lucide-react';
import {
  SRI_LANKAN_DISTRICTS,
  listCouriers,
  registerCourier,
  resendCourierInvite,
  updateCourierStatus,
  type UserProfileItem,
  type UserStatus
} from '@/services/api/authApi';
import {
  dispatchApi,
  type CourierProfileResponse,
  type CourierShiftResponse,
  type SriLankanDistrict,
  type VehicleType
} from '@/services/api/dispatchApi';
import { parseApiError } from '@/services/api/apiError';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Modal } from '@/design-system/primitives/Modal';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { useAuthStore } from '@/store/useAuthStore';
import { VEHICLE_TYPES, isValidPlate, normalisePlate, vehicleLabel } from '@/utils/vehicles';

const selectClass =
  'h-10 w-full rounded-lg border border-border-subtle bg-card px-3 text-sm text-primary focus-visible:outline-border-focus';

function districtName(value?: string | null) {
  return value
    ? value
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
    : '—';
}

function errorText(error: unknown, fallback: string) {
  return parseApiError(error).message ?? fallback;
}

type AccountBadge = { label: string; className: string };

function accountBadge(courier: UserProfileItem): AccountBadge {
  if (courier.status === 'SUSPENDED') return { label: 'Suspended', className: 'bg-status-warning-bg text-status-warning-fg' };
  if (courier.status === 'DEACTIVATED') return { label: 'Deactivated', className: 'bg-status-danger-bg text-status-danger-fg' };
  if (courier.invitePending) {
    const expired = courier.inviteExpiresAt ? new Date(courier.inviteExpiresAt) < new Date() : false;
    return expired
      ? { label: 'Invite expired', className: 'bg-status-danger-bg text-status-danger-fg' }
      : { label: 'Invite sent', className: 'bg-brand-subtle text-brand-primary' };
  }
  return { label: 'Active', className: 'bg-status-success-bg text-status-success-fg' };
}

/**
 * Dispatchers register and manage the couriers of their own district. A new courier gets an email
 * invite, chooses a password and adds their own vehicle; the dispatcher can then correct the vehicle,
 * resend an invite, or suspend and reactivate the courier.
 */
export function CourierManagementPage() {
  const user = useAuthStore((state) => state.user);
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  const client = useQueryClient();
  const [search, setSearch] = useState('');
  const [notice, setNotice] = useState('');
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<{ courier: UserProfileItem; profile: CourierProfileResponse }>();
  const [statusChange, setStatusChange] = useState<{ courier: UserProfileItem; status: UserStatus }>();
  const [rowError, setRowError] = useState('');
  const [busyId, setBusyId] = useState<string>();

  const couriers = useQuery({ queryKey: ['couriers'], queryFn: () => listCouriers(), retry: false });
  const profiles = useQuery({ queryKey: ['courier-profiles'], queryFn: dispatchApi.getCourierProfiles, retry: false });
  const shifts = useQuery({
    queryKey: ['courier-shifts', user?.district, isAdmin],
    queryFn: () =>
      dispatchApi.getCouriers(isAdmin ? undefined : { district: user?.district as SriLankanDistrict }),
    retry: false
  });

  const profileById = useMemo(
    () => new Map((profiles.data ?? []).map((profile) => [profile.courierId, profile])),
    [profiles.data]
  );
  const shiftById = useMemo(
    () => new Map((shifts.data ?? []).map((shift: CourierShiftResponse) => [shift.courierId, shift])),
    [shifts.data]
  );

  const term = search.trim().toLowerCase();
  const rows = (couriers.data ?? []).filter((courier) =>
    !term
      ? true
      : [courier.firstName, courier.lastName, courier.username, courier.email, courier.phone]
          .filter(Boolean)
          .some((value) => value!.toLowerCase().includes(term))
  );
  const pending = (couriers.data ?? []).filter((courier) => courier.invitePending).length;
  const withoutVehicle = (couriers.data ?? []).filter(
    (courier) => !courier.invitePending && courier.id && !profileById.has(courier.id)
  ).length;

  async function refresh() {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['couriers'] }),
      client.invalidateQueries({ queryKey: ['courier-profiles'] }),
      client.invalidateQueries({ queryKey: ['courier-shifts'] })
    ]);
  }

  async function resend(courier: UserProfileItem) {
    if (!courier.id) return;
    setBusyId(courier.id);
    setRowError('');
    setNotice('');
    try {
      await resendCourierInvite(courier.id);
      setNotice(`A new invite was emailed to ${courier.email}. The previous link no longer works.`);
      await refresh();
    } catch (error) {
      setRowError(errorText(error, 'Could not resend the invite.'));
    } finally {
      setBusyId(undefined);
    }
  }

  async function confirmStatusChange() {
    if (!statusChange?.courier.id) return;
    setBusyId(statusChange.courier.id);
    setRowError('');
    try {
      await updateCourierStatus(statusChange.courier.id, statusChange.status);
      setNotice(
        `${statusChange.courier.firstName ?? statusChange.courier.username} was ${
          statusChange.status === 'ACTIVE' ? 'reactivated' : 'suspended'
        }.`
      );
      setStatusChange(undefined);
      await refresh();
    } catch (error) {
      setRowError(errorText(error, 'Could not change the courier status.'));
    } finally {
      setBusyId(undefined);
    }
  }

  const loadError = couriers.error ?? profiles.error;

  return (
    <div className="mx-auto max-w-[1440px] space-y-6 p-4 md:p-7 lg:p-8" data-density="compact">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-widest text-brand-primary">
            {isAdmin ? 'All districts' : `${districtName(user?.district)} district`}
          </p>
          <h1 className="text-3xl font-bold tracking-tight">Couriers</h1>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            density="compact"
            disabled={couriers.isFetching}
            leftIcon={<RefreshCw className={couriers.isFetching ? 'animate-spin' : ''} />}
            onClick={() => void refresh()}
          >
            Refresh
          </Button>
          <Button density="compact" leftIcon={<UserPlus />} onClick={() => setAdding(true)}>
            Add courier
          </Button>
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-3">
        {[
          { icon: <Users size={21} />, label: 'Couriers', value: couriers.data?.length, tone: 'bg-brand-subtle text-brand-primary' },
          { icon: <Mail size={21} />, label: 'Waiting to accept invite', value: pending, tone: 'bg-canvas text-secondary' },
          { icon: <Bike size={21} />, label: 'No vehicle added yet', value: withoutVehicle, tone: 'bg-status-warning-bg text-status-warning-fg' }
        ].map((stat) => (
          <SurfaceCard key={stat.label} density="compact" className="rounded-xl shadow-sm">
            <div className="flex items-center gap-3 p-2">
              <span className={`rounded-xl p-3 ${stat.tone}`}>{stat.icon}</span>
              <div>
                <p className="text-xs text-secondary">{stat.label}</p>
                <p className="mt-1 text-2xl font-semibold tabular-nums">{couriers.data ? stat.value : '—'}</p>
              </div>
            </div>
          </SurfaceCard>
        ))}
      </div>

      {notice && (
        <div
          role="status"
          className="flex items-center gap-2 rounded-xl border border-status-success-border bg-status-success-bg px-4 py-3 text-sm text-status-success-fg"
        >
          <CheckCircle2 size={18} />
          {notice}
        </div>
      )}
      {(rowError || loadError) && (
        <div
          role="alert"
          className="rounded-xl border border-status-danger-border bg-status-danger-bg px-4 py-3 text-sm text-status-danger-fg"
        >
          {rowError || errorText(loadError, 'Could not load couriers.')}
        </div>
      )}

      <SurfaceCard density="compact" className="overflow-hidden rounded-xl shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle px-2 pb-4 pt-2">
          <h2 className="font-semibold">
            Courier directory{' '}
            <span className="ml-2 rounded-full bg-canvas px-2.5 py-1 text-xs font-medium text-secondary">
              {rows.length}
            </span>
          </h2>
          <label className="flex h-10 min-w-56 items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 focus-within:ring-2 focus-within:ring-border-focus">
            <Search size={17} />
            <input
              type="search"
              aria-label="Search couriers"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name, username or phone…"
              className="min-w-0 flex-1 bg-transparent text-sm text-primary outline-none placeholder:text-muted"
            />
          </label>
        </div>

        {couriers.isPending ? (
          <p role="status" className="py-20 text-center text-sm text-secondary">
            Loading couriers…
          </p>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-16 text-center">
            <span className="rounded-full bg-canvas p-4 text-muted">
              <Users size={26} />
            </span>
            <p className="font-semibold">{term ? 'No couriers match your search' : 'No couriers yet'}</p>
            <p className="text-sm text-secondary">
              {term ? 'Try another name or phone number.' : 'Add your first courier. They will get an email invite.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm" aria-busy={couriers.isFetching}>
              <caption className="sr-only">Couriers with their vehicle, shift and account status</caption>
              <thead>
                <tr className="border-b border-border-subtle bg-canvas text-xs text-secondary">
                  {['Courier', 'Account', 'Vehicle', 'Shift', ...(isAdmin ? ['District'] : []), 'Actions'].map((label) => (
                    <th key={label} scope="col" className={`px-4 py-3 font-medium ${label === 'Actions' ? 'text-right' : ''}`}>
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {rows.map((courier) => {
                  const badge = accountBadge(courier);
                  const profile = courier.id ? profileById.get(courier.id) : undefined;
                  const shift = courier.id ? shiftById.get(courier.id) : undefined;
                  const busy = busyId === courier.id;
                  return (
                    <tr key={courier.id} className="transition-colors hover:bg-canvas">
                      <td className="px-4 py-3">
                        <p className="font-semibold text-primary">
                          {[courier.firstName, courier.lastName].filter(Boolean).join(' ') || courier.username}
                        </p>
                        <p className="mt-0.5 text-xs text-secondary">
                          {courier.username} · <span className="tabular-nums">{courier.phone ?? courier.email}</span>
                        </p>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${badge.className}`}>
                          <span className="size-1.5 rounded-full bg-current" />
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {profile ? (
                          <>
                            <p className="font-mono text-sm font-semibold tabular-nums">{profile.plateNumber}</p>
                            <p className="mt-0.5 text-xs text-secondary">
                              {vehicleLabel(profile.vehicleType)} · {profile.vehicleModel} · {profile.maxCapacity} parcels
                            </p>
                          </>
                        ) : (
                          <span className="text-xs text-muted">
                            {courier.invitePending ? 'Added after the invite' : 'Not added yet'}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-secondary">
                        {shift?.shiftStatus && shift.shiftStatus !== 'OFFLINE'
                          ? `${shift.shiftStatus.replaceAll('_', ' ').toLowerCase()} · ${shift.activeParcelsCount ?? 0}/${shift.maxCapacity ?? 0}`
                          : 'Off shift'}
                      </td>
                      {isAdmin && <td className="px-4 py-3 text-xs text-secondary">{districtName(courier.district)}</td>}
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-2">
                          {courier.invitePending && courier.status === 'ACTIVE' && (
                            <Button variant="outline" density="compact" disabled={busy} leftIcon={<Mail />} onClick={() => void resend(courier)}>
                              Resend invite
                            </Button>
                          )}
                          {profile && (
                            <Button
                              variant="outline"
                              density="compact"
                              disabled={busy}
                              leftIcon={<Pencil />}
                              onClick={() => setEditing({ courier, profile })}
                            >
                              Vehicle
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            density="compact"
                            disabled={busy}
                            leftIcon={courier.status === 'ACTIVE' ? <UserX /> : <UserCheck />}
                            onClick={() =>
                              setStatusChange({ courier, status: courier.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' })
                            }
                          >
                            {courier.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
                          </Button>
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

      <AddCourierModal
        open={adding}
        isAdmin={isAdmin}
        defaultDistrict={(user?.district as SriLankanDistrict) ?? 'COLOMBO'}
        onClose={() => setAdding(false)}
        onAdded={async (courier) => {
          setAdding(false);
          setNotice(`${courier.firstName} was registered. An invite is on its way to ${courier.email}.`);
          await refresh();
        }}
      />

      {editing && (
        <EditVehicleModal
          courier={editing.courier}
          profile={editing.profile}
          onClose={() => setEditing(undefined)}
          onSaved={async () => {
            setNotice(`Vehicle updated for ${editing.courier.firstName ?? editing.courier.username}.`);
            setEditing(undefined);
            await refresh();
          }}
        />
      )}

      <Modal
        open={Boolean(statusChange)}
        size="sm"
        onClose={() => !busyId && setStatusChange(undefined)}
        title={statusChange?.status === 'ACTIVE' ? 'Reactivate courier?' : 'Suspend courier?'}
        description={
          statusChange?.status === 'ACTIVE'
            ? 'The courier will be able to sign in and take deliveries again.'
            : 'The courier is signed out at once and cannot sign in until you reactivate them.'
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="outline" density="compact" disabled={Boolean(busyId)} onClick={() => setStatusChange(undefined)}>
              Cancel
            </Button>
            <Button
              variant={statusChange?.status === 'ACTIVE' ? 'primary' : 'destructive'}
              density="compact"
              loading={Boolean(busyId)}
              loadingLabel="Updating…"
              onClick={() => void confirmStatusChange()}
            >
              {statusChange?.status === 'ACTIVE' ? 'Reactivate' : 'Suspend'}
            </Button>
          </div>
        }
      >
        {statusChange && (
          <div className="rounded-lg border border-border-subtle bg-canvas p-3">
            <p className="font-semibold">
              {[statusChange.courier.firstName, statusChange.courier.lastName].filter(Boolean).join(' ')}
            </p>
            <p className="mt-1 text-sm text-secondary">{statusChange.courier.username}</p>
          </div>
        )}
      </Modal>
    </div>
  );
}

function AddCourierModal({
  open,
  isAdmin,
  defaultDistrict,
  onClose,
  onAdded
}: {
  open: boolean;
  isAdmin: boolean;
  defaultDistrict: SriLankanDistrict;
  onClose: () => void;
  onAdded: (courier: UserProfileItem) => void | Promise<void>;
}) {
  const empty = { firstName: '', lastName: '', username: '', email: '', phone: '' };
  const [form, setForm] = useState(empty);
  const [district, setDistrict] = useState<SriLankanDistrict>(defaultDistrict);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const set = (key: keyof typeof empty) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));

  function close() {
    if (saving) return;
    setForm(empty);
    setError('');
    onClose();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const courier = await registerCourier({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        phone: form.phone.replace(/\s/g, ''),
        ...(isAdmin ? { district } : {})
      });
      setForm(empty);
      await onAdded(courier);
    } catch (requestError) {
      setError(errorText(requestError, 'Could not register the courier.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Add courier"
      description="We'll email them an invite. The link is valid for 72 hours."
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" density="compact" disabled={saving} onClick={close}>
            Cancel
          </Button>
          <Button type="submit" form="add-courier-form" density="compact" loading={saving} loadingLabel="Registering…">
            Register and send invite
          </Button>
        </div>
      }
    >
      <form id="add-courier-form" className="grid gap-3 sm:grid-cols-2" onSubmit={submit}>
        <Field label="First name" required density="compact" value={form.firstName} onChange={set('firstName')} maxLength={100} />
        <Field label="Last name" required density="compact" value={form.lastName} onChange={set('lastName')} maxLength={100} />
        <Field
          label="Username"
          required
          density="compact"
          value={form.username}
          onChange={set('username')}
          pattern="[A-Za-z0-9._\-]{3,50}"
          hint="Letters, digits, dot, dash or underscore"
          autoComplete="off"
        />
        <Field
          label="Mobile number"
          required
          density="compact"
          variant="phone"
          value={form.phone}
          onChange={set('phone')}
          inputMode="tel"
        />
        <Field
          label="Email"
          required
          density="compact"
          inputMode="email"
          autoComplete="off"
          value={form.email}
          onChange={set('email')}
          className="sm:col-span-2"
          hint="The invite is sent here"
        />
        {isAdmin && (
          <label className="flex flex-col gap-1.5 text-sm font-semibold text-primary sm:col-span-2">
            District
            <select className={selectClass} value={district} onChange={(event) => setDistrict(event.target.value as SriLankanDistrict)}>
              {SRI_LANKAN_DISTRICTS.map((value) => (
                <option key={value} value={value}>
                  {districtName(value)}
                </option>
              ))}
            </select>
          </label>
        )}
        {error && (
          <p role="alert" className="text-sm text-status-danger sm:col-span-2">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

function EditVehicleModal({
  courier,
  profile,
  onClose,
  onSaved
}: {
  courier: UserProfileItem;
  profile: CourierProfileResponse;
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}) {
  const [vehicleType, setVehicleType] = useState<VehicleType>(profile.vehicleType ?? 'MOTORCYCLE');
  const [plateNumber, setPlateNumber] = useState(profile.plateNumber ?? '');
  const [vehicleModel, setVehicleModel] = useState(profile.vehicleModel ?? '');
  const [vehicleColour, setVehicleColour] = useState(profile.vehicleColour ?? '');
  const [licenceNumber, setLicenceNumber] = useState(profile.licenceNumber ?? '');
  const [capacity, setCapacity] = useState(String(profile.maxCapacity ?? ''));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  function changeType(next: VehicleType) {
    setVehicleType(next);
    // A new vehicle type starts from its own default capacity; the dispatcher can still adjust it.
    setCapacity(String(VEHICLE_TYPES.find((vehicle) => vehicle.value === next)?.capacity ?? ''));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!courier.id) return;
    if (!isValidPlate(plateNumber)) {
      setError('Enter the number plate as shown on the vehicle, for example WP BAB-1234.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await dispatchApi.updateCourierProfile(courier.id, {
        vehicleType,
        plateNumber: normalisePlate(plateNumber),
        vehicleModel: vehicleModel.trim(),
        vehicleColour: vehicleColour.trim(),
        licenceNumber: licenceNumber.trim(),
        maxCapacity: capacity ? Number(capacity) : undefined
      });
      await onSaved();
    } catch (requestError) {
      setError(errorText(requestError, 'Could not update the vehicle.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      onClose={() => !saving && onClose()}
      title={`Vehicle for ${courier.firstName ?? courier.username}`}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" density="compact" disabled={saving} onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="edit-vehicle-form" density="compact" loading={saving} loadingLabel="Saving…">
            Save vehicle
          </Button>
        </div>
      }
    >
      <form id="edit-vehicle-form" className="grid gap-3 sm:grid-cols-2" onSubmit={submit}>
        <label className="flex flex-col gap-1.5 text-sm font-semibold text-primary">
          Vehicle type
          <select className={selectClass} value={vehicleType} onChange={(event) => changeType(event.target.value as VehicleType)}>
            {VEHICLE_TYPES.map((vehicle) => (
              <option key={vehicle.value} value={vehicle.value}>
                {vehicle.label}
              </option>
            ))}
          </select>
        </label>
        <Field
          label="Parcels at once"
          required
          density="compact"
          inputMode="numeric"
          pattern="[0-9]{1,3}"
          value={capacity}
          onChange={(event) => setCapacity(event.target.value.replace(/\D/g, '').slice(0, 3))}
          hint="1 to 200"
          inputClassName="tabular-nums"
        />
        <Field
          label="Number plate"
          required
          density="compact"
          value={plateNumber}
          onChange={(event) => setPlateNumber(event.target.value)}
          onBlur={() => plateNumber && setPlateNumber(normalisePlate(plateNumber))}
          inputClassName="font-mono uppercase"
        />
        <Field
          label="Driving licence"
          required
          density="compact"
          value={licenceNumber}
          onChange={(event) => setLicenceNumber(event.target.value.toUpperCase())}
          inputClassName="font-mono"
        />
        <Field label="Make and model" required density="compact" value={vehicleModel} onChange={(event) => setVehicleModel(event.target.value)} />
        <Field label="Colour" required density="compact" value={vehicleColour} onChange={(event) => setVehicleColour(event.target.value)} />
        {error && (
          <p role="alert" className="text-sm text-status-danger sm:col-span-2">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

export default CourierManagementPage;
