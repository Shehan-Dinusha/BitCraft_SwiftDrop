import { useState, useEffect, useCallback, type ReactNode } from 'react';
import { phoneDigits, phoneProblem, toE164 } from '@/utils/phone';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import {
  dispatchApi,
  type CourierProfileResponse,
  type CourierShiftResponse,
  type ShiftStatus,
  type WorkdayResponse
} from '@/services/api/dispatchApi';
import { deliveryApi, type DeliverySessionResponse } from '@/services/api/deliveryApi';
import { fetchCurrentUser } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { isOpenForCourier } from '@/types/fsm';
import { lkr } from '@/features/cash/cashDisplay';
import { cashApi } from '@/services/api/cashApi';
import { vehicleLabel } from '@/utils/vehicles';
import { districtLabel } from '@/features/profile/profileFormat';
import { colomboClock, colomboTimeNow, dayLabel, hhmm } from '@/utils/colomboDate';
import { SPEED_RANK } from '@/features/admin-ops/components/dispatch/dispatchQueues';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import {
  Bike,
  Coffee,
  MapPin,
  Play,
  Truck,
  Warehouse,
  Package,
  Store,
  Power,
  RefreshCw,
  Clock,
  ChevronRight
} from 'lucide-react';

const STATUS_LABEL: Record<string, string> = {
  AVAILABLE: 'Available',
  BUSY: 'Full',
  ON_BREAK: 'On break',
  OFFLINE: 'Off shift'
};

/** Waiting at the hub for this courier: a delivery to take out, or a return to bring to the sender. */
const isAtHub = (job: DeliverySessionResponse) =>
  job.currentState === 'AT_DESTINATION_HUB' || job.currentState === 'RETURN_AT_HUB';

/** A merchant's parcel still at the shop: collected with the shop's others. */
const isShopStop = (job: DeliverySessionResponse) =>
  job.currentState === 'ASSIGNED' && Boolean(job.merchantId);

function groupShops(jobs: DeliverySessionResponse[]) {
  const shops = new Map<string, { merchantId: string; name: string; count: number }>();
  for (const job of jobs) {
    if (!job.merchantId || !isShopStop(job)) continue;
    const shop = shops.get(job.merchantId) ?? {
      merchantId: job.merchantId,
      name: 'Shop pickup',
      count: 0
    };
    shops.set(job.merchantId, {
      ...shop,
      name: job.merchantName ?? shop.name,
      count: shop.count + 1
    });
  }
  return [...shops.values()];
}

export function CourierShiftDashboard() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const courierId = user?.id;

  const [shift, setShift] = useState<CourierShiftResponse | null>(null);
  const [todayDeliveries, setTodayDeliveries] = useState<DeliverySessionResponse[]>([]);
  const [cashInHand, setCashInHand] = useState(0);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null
  );
  // undefined while loading; null when the courier has not registered a vehicle yet.
  const [vehicle, setVehicle] = useState<CourierProfileResponse | null | undefined>(undefined);
  const [workday, setWorkday] = useState<WorkdayResponse | null>(null);
  // Hub parcels the courier unticked; new ones arrive ticked.
  const [leftBehind, setLeftBehind] = useState<Set<string>>(new Set());

  const fetchShiftData = useCallback(async () => {
    if (!courierId) return;
    try {
      setLoading(true);
      setErrorMsg(null);

      // The courier's own registered vehicle; without one a shift cannot start.
      try {
        setVehicle(await dispatchApi.getMyProfile());
      } catch {
        setVehicle(undefined);
      }

      // Hours, closed days and the lunch break; the server enforces them either way.
      try {
        setWorkday(await dispatchApi.getWorkday());
      } catch {
        setWorkday(null);
      }

      // Fetch courier shift data
      try {
        const shiftData = await dispatchApi.getCourier(courierId);
        setShift(shiftData);
      } catch {
        setShift(null);
      }

      try {
        setCashInHand((await cashApi.mine()).inHand);
      } catch {
        // The server still refuses to end a shift with cash in hand.
        setCashInHand(0);
      }

      // Fetch today's deliveries from delivery-execution-service (/api/v1/deliveries/me)
      try {
        const myDeliveries = await deliveryApi.getMyDeliveries('today');
        setTodayDeliveries(myDeliveries || []);
      } catch {
        setErrorMsg('Your jobs could not be loaded. Tap Sync to try again.');
      }
    } finally {
      setLoading(false);
    }
  }, [courierId]);

  useEffect(() => {
    fetchShiftData();
  }, [fetchShiftData]);

  const handleStartShift = async () => {
    if (!courierId) return;
    try {
      setUpdating(true);
      setErrorMsg(null);
      // Starting a shift updates the courier's dispatch record, so send their real profile:
      // the name and phone dispatchers see come from the account, not placeholders.
      const profile = await fetchCurrentUser().catch(() => null);
      const fullName = [profile?.firstName, profile?.lastName].filter(Boolean).join(' ');
      const phone = phoneDigits(profile?.phone || shift?.phone || '');
      const res = await dispatchApi.startShift({
        courierId,
        courierName: fullName || shift?.courierName || user?.username || 'Courier',
        // An account phone saved before today's rule is left out rather than blocking the shift.
        phone: phone && !phoneProblem(phone) ? toE164(phone) : undefined
      });
      setShift(res);
      setSuccessMsg('Shift started! You can now get jobs.');
      await fetchShiftData();
    } catch (err: unknown) {
      setErrorMsg(parseApiError(err).message ?? 'Failed to start shift');
    } finally {
      setUpdating(false);
    }
  };

  const handleUpdateStatus = async (newStatus: ShiftStatus) => {
    if (!courierId) return;
    try {
      setUpdating(true);
      setErrorMsg(null);
      const res = await dispatchApi.updateShiftStatus({
        courierId,
        status: newStatus
      });
      setShift(res);
      setSuccessMsg(
        newStatus === 'ON_BREAK'
          ? `Enjoy your lunch. Back by ${colomboClock(res.breakEndsAt)}.`
          : 'Welcome back.'
      );
    } catch (err: unknown) {
      setErrorMsg(parseApiError(err).message ?? 'Your break could not be updated');
    } finally {
      setUpdating(false);
    }
  };

  const handleLoad = async (deliveryIds: string[]) => {
    try {
      setUpdating(true);
      setErrorMsg(null);
      const loaded = await deliveryApi.loadAtHub(deliveryIds);
      setSuccessMsg(`Loaded ${loaded.length} ${loaded.length === 1 ? 'parcel' : 'parcels'}.`);
      setLeftBehind(new Set());
      await fetchShiftData();
    } catch (err: unknown) {
      setErrorMsg(parseApiError(err).message ?? 'The parcels could not be loaded');
      await fetchShiftData();
    } finally {
      setUpdating(false);
    }
  };

  const handleStartRun = async () => {
    if (!courierId) return;
    try {
      setUpdating(true);
      setErrorMsg(null);
      setShift(await dispatchApi.startRun(courierId));
      setSuccessMsg('On your way. New pickups can still come in.');
    } catch (err: unknown) {
      setErrorMsg(parseApiError(err).message ?? 'The run could not be started');
    } finally {
      setUpdating(false);
    }
  };

  const handleEndShift = async () => {
    if (!courierId || !window.confirm('Are you sure you want to end your shift?')) return;
    try {
      setUpdating(true);
      setErrorMsg(null);
      const res = await dispatchApi.endShift(courierId);
      setShift(res);
      setSuccessMsg('Shift ended. Good work today!');
    } catch (err: unknown) {
      setErrorMsg(parseApiError(err).message ?? 'Failed to end shift');
    } finally {
      setUpdating(false);
    }
  };

  // Same day first, then Express: the order a courier should work through.
  const openJobs = todayDeliveries
    .filter((job) => isOpenForCourier(job.currentState))
    .sort((a, b) => (SPEED_RANK[a.speed ?? ''] ?? 2) - (SPEED_RANK[b.speed ?? ''] ?? 2));
  // At the hub first, then pickups (a shop's parcels are one stop), then what is on board.
  const atHub = openJobs.filter(isAtHub);
  const shops = groupShops(openJobs);
  const pickups = openJobs.filter((job) => job.currentState === 'ASSIGNED' && !isShopStop(job));
  const onBoard = openJobs.filter((job) => job.currentState !== 'ASSIGNED' && !isAtHub(job));
  const toLoad = atHub.filter((job) => job.deliveryId && !leftBehind.has(job.deliveryId));
  const onRun = Boolean(shift?.runStartedAt);
  const isOnline = shift && shift.shiftStatus !== 'OFFLINE';
  const capacityUsed = shift ? (shift.activeParcelsCount ?? 0) : 0;
  const maxCap = shift?.maxCapacity || 5;
  const capacityPct = Math.min(100, Math.round((capacityUsed / maxCap) * 100));

  const status = shift?.shiftStatus ?? 'OFFLINE';
  const onBreak = status === 'ON_BREAK';
  const working = isOnline && status !== 'ON_BREAK';
  const now = colomboTimeNow();
  const closedToday = workday ? !workday.open : false;
  const cannotStart = workday ? !workday.shiftCanStart : false;
  const breakTaken = Boolean(shift?.breakStartedAt);
  const inBreakWindow = workday
    ? now >= hhmm(workday.breakFrom) && now <= hhmm(workday.breakUntil)
    : true;
  const breakOver = onBreak && Boolean(shift?.breakEndsAt) && Date.now() > Date.parse(shift?.breakEndsAt ?? '');
  const tone = !isOnline
    ? 'border-l-border-strong bg-canvas'
    : status === 'AVAILABLE'
      ? 'border-l-status-success bg-status-success-bg'
      : 'border-l-status-warning bg-status-warning-bg';
  const iconTone = !isOnline ? 'bg-muted' : status === 'AVAILABLE' ? 'bg-status-success' : 'bg-status-warning';
  const pillTone = !isOnline
    ? 'bg-border-subtle text-primary'
    : status === 'AVAILABLE'
      ? 'bg-status-success-bg text-status-success-fg'
      : 'bg-status-warning-bg text-status-warning-fg';

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-4 md:p-6" data-density="touch">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">Today</h1>
        </div>
        <Button
          variant="outline"
          onClick={fetchShiftData}
          disabled={loading || updating}
          className="self-start text-xs sm:self-auto"
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Sync
        </Button>
      </header>

      {errorMsg && (
        <StatusBanner tone="danger" title="Dispatch Notice" onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </StatusBanner>
      )}
      {successMsg && (
        <StatusBanner tone="success" title="Success" onDismiss={() => setSuccessMsg(null)}>
          {successMsg}
        </StatusBanner>
      )}

      {/* Online / Offline Status Banner Card */}
      <SurfaceCard variant="elevated" className={`border-l-4 p-5 ${tone}`}>
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-12 w-12 items-center justify-center rounded-xl text-brand-on ${iconTone}`}
            >
              <Bike className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-primary">
                  {user?.username || shift?.courierName || 'Courier'}
                </span>
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${pillTone}`}
                >
                  {STATUS_LABEL[status] ?? status}
                </span>
              </div>
              <p className="flex items-center gap-1.5 text-xs text-secondary">
                <MapPin className="h-3.5 w-3.5 text-brand-primary" />
                {districtLabel(user?.district ?? shift?.currentDistrict)}
              </p>
            </div>
          </div>

          {/* Touch-Friendly Action Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            {!isOnline && vehicle === null ? (
              <Button
                variant="primary"
                onClick={() => navigate('/courier/vehicle')}
                className="min-h-target w-full px-6 font-semibold shadow-md sm:w-auto"
              >
                <Bike className="mr-2 h-5 w-5" />
                Add your vehicle to start
              </Button>
            ) : !isOnline ? (
              closedToday ? null : (
                <Button
                  variant="primary"
                  onClick={handleStartShift}
                  disabled={updating || cannotStart}
                  className="min-h-target w-full px-6 font-semibold shadow-md sm:w-auto"
                >
                  <Power className="mr-2 h-5 w-5" />
                  Start shift
                </Button>
              )
            ) : (
              <>
                {onBreak ? (
                  <Button
                    variant="primary"
                    onClick={() => handleUpdateStatus('AVAILABLE')}
                    disabled={updating}
                    className="min-h-target"
                  >
                    <Play className="mr-1.5 h-4 w-4" />
                    End break
                  </Button>
                ) : breakTaken ? null : (
                  <Button
                    variant="outline"
                    onClick={() => handleUpdateStatus('ON_BREAK')}
                    disabled={updating || !inBreakWindow}
                    className="min-h-target"
                  >
                    <Coffee className="mr-1.5 h-4 w-4" />
                    Lunch break
                  </Button>
                )}
                <Button
                  variant="destructive"
                  onClick={handleEndShift}
                  disabled={updating || capacityUsed > 0 || cashInHand > 0}
                  className="min-h-target"
                  title={
                    capacityUsed > 0
                      ? 'Deliver active parcels before ending shift'
                      : cashInHand > 0
                        ? 'Hand over your cash before ending shift'
                        : 'End shift'
                  }
                >
                  <Power className="mr-1.5 h-4 w-4" />
                  End shift
                </Button>
              </>
            )}
          </div>
        </div>
      </SurfaceCard>

      {!isOnline && workday && closedToday && (
        <StatusBanner tone="info" title={`Closed today (${workday.closedReason ?? 'holiday'})`}>
          Next working day: {dayLabel(workday.nextWorkingDay)}.
        </StatusBanner>
      )}
      {!isOnline && workday && !closedToday && cannotStart && (
        <StatusBanner tone="info" title="Outside working hours">
          {now < hhmm(workday.shiftFrom)
            ? `Shifts start at ${hhmm(workday.shiftFrom)}.`
            : `The working day ended at ${hhmm(workday.closesAt)}. Next working day: ${dayLabel(workday.nextWorkingDay)}.`}
        </StatusBanner>
      )}
      {onBreak && (
        <StatusBanner
          tone={breakOver ? 'warning' : 'info'}
          title={
            breakOver
              ? `Your break ended at ${colomboClock(shift?.breakEndsAt)}`
              : `On lunch break until ${colomboClock(shift?.breakEndsAt)}`
          }
        >
          New jobs can still come in. You will see them here.
        </StatusBanner>
      )}
      {isOnline && onRun && (
        <p className="flex items-center gap-1.5 text-caption text-secondary">
          <Truck aria-hidden="true" className="h-4 w-4" />
          On the road since {colomboClock(shift?.runStartedAt)}. Only pickups can be added now.
        </p>
      )}
      {working && workday && !breakTaken && !inBreakWindow && now < hhmm(workday.breakUntil) && (
        <p className="text-caption text-secondary">
          Lunch break from {hhmm(workday.breakFrom)} to {hhmm(workday.breakUntil)}, up to{' '}
          {workday.breakMinutes} minutes.
        </p>
      )}

      {cashInHand > 0 && (
        <StatusBanner tone="warning" title={`${lkr(cashInHand)} in hand`}>
          <span className="block">Hand it over before you end your shift.</span>
          <Button
            variant="outline"
            className="mt-2 min-h-target"
            onClick={() => navigate('/courier/cash')}
          >
            Open Cash
          </Button>
        </StatusBanner>
      )}

      {/* Capacity & Shift Metrics */}
      {isOnline && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-secondary">Parcels on Board</span>
              <Package className="h-4 w-4 text-brand-primary" />
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-2xl font-bold text-primary">{capacityUsed}</span>
              <span className="text-xs text-secondary">/ {maxCap} max</span>
            </div>
            <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-canvas">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  capacityPct >= 80 ? 'bg-status-warning' : 'bg-brand-primary'
                }`}
                style={{ width: `${capacityPct}%` }}
              />
            </div>
          </SurfaceCard>

          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-secondary">Vehicle Type</span>
              <Bike className="h-4 w-4 text-status-success-fg" />
            </div>
            <div className="mt-2 text-xl font-bold text-primary">
              {vehicleLabel(vehicle?.vehicleType ?? shift?.vehicleType)}
            </div>
            <p className="mt-1 font-mono text-xs tabular-nums text-secondary">
              {vehicle?.plateNumber ?? 'Registered vehicle'}
            </p>
          </SurfaceCard>

          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-secondary">Shift Started</span>
              <Clock className="h-4 w-4 text-status-info-fg" />
            </div>
            <div className="mt-2 text-sm font-semibold text-primary">
              {shift?.shiftStartedAt
                ? new Date(shift.shiftStartedAt).toLocaleTimeString([], {
                    hour: '2-digit',
                    minute: '2-digit'
                  })
                : 'Not started'}
            </div>
          </SurfaceCard>
        </div>
      )}

      {isOnline && !onRun && (
        <SurfaceCard className="space-y-3 p-4">
          <div className="flex items-center gap-2">
            <Warehouse aria-hidden="true" className="h-5 w-5 text-brand-primary" />
            <h2 className="text-lg font-bold text-primary">At the hub</h2>
          </div>
          {atHub.length > 0 ? (
            <>
              <ul className="divide-y divide-border-subtle" aria-label="Parcels to load">
                {atHub.map((job) => {
                  const id = job.deliveryId ?? '';
                  return (
                    <li key={id}>
                      <label className="flex min-h-target cursor-pointer items-center gap-3 py-1">
                        <span className="sr-only">Load</span>
                        <input
                          type="checkbox"
                          className="size-5 accent-brand-primary"
                          checked={!leftBehind.has(id)}
                          disabled={updating}
                          onChange={(event) => {
                            const next = new Set(leftBehind);
                            if (event.target.checked) next.delete(id);
                            else next.add(id);
                            setLeftBehind(next);
                          }}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-mono text-sm font-bold tabular-nums text-primary">
                            {job.trackingNumber}
                          </span>
                          <span className="block text-caption text-secondary">
                            {job.currentState === 'RETURN_AT_HUB'
                              ? `Return to the sender, ${districtLabel(job.pickupDistrict)}`
                              : `Deliver in ${districtLabel(job.deliveryDistrict)}`}
                          </span>
                        </span>
                      </label>
                    </li>
                  );
                })}
              </ul>
              <Button
                variant="primary"
                className="min-h-target w-full"
                disabled={updating || toLoad.length === 0}
                onClick={() => handleLoad(toLoad.map((job) => job.deliveryId ?? ''))}
              >
                <Package className="mr-2 h-5 w-5" />
                Load {toLoad.length} {toLoad.length === 1 ? 'parcel' : 'parcels'}
              </Button>
            </>
          ) : (
            <p className="text-body text-secondary">Nothing waiting for you at the hub.</p>
          )}
          <Button
            variant={atHub.length > 0 ? 'outline' : 'primary'}
            className="min-h-target w-full"
            disabled={updating || onBreak || atHub.length > 0}
            onClick={handleStartRun}
          >
            <Truck className="mr-2 h-5 w-5" />
            Start run
          </Button>
        </SurfaceCard>
      )}

      <JobList
        title="Pickups"
        empty={isOnline ? 'No pickups yet' : 'Start your shift to get jobs'}
        count={shops.length + pickups.length}
      >
        {shops.map((shop) => (
          <button
            key={shop.merchantId}
            type="button"
            onClick={() => navigate(`/courier/shops/${shop.merchantId}`)}
            className="flex min-h-target w-full items-center justify-between gap-3 rounded-xl border border-border-subtle bg-card px-4 py-2 text-left hover:border-brand-primary hover:bg-canvas"
          >
            <span className="flex min-w-0 items-center gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-subtle text-brand-primary">
                <Store aria-hidden="true" className="size-5" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-body-strong text-primary">{shop.name}</span>
                <span className="block text-caption text-secondary">
                  Pick up {shop.count} {shop.count === 1 ? 'parcel' : 'parcels'}
                </span>
              </span>
            </span>
            <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted" />
          </button>
        ))}
        {pickups.map((job) => (
          <JobRow key={job.deliveryId} job={job} onOpen={() => navigate(`/courier/delivery/${job.deliveryId}`)} />
        ))}
      </JobList>

      {onBoard.length > 0 && (
        <JobList title="On board" count={onBoard.length}>
          {onBoard.map((job) => (
            <JobRow key={job.deliveryId} job={job} onOpen={() => navigate(`/courier/delivery/${job.deliveryId}`)} />
          ))}
        </JobList>
      )}
    </main>
  );
}

function JobList({
  title,
  count,
  empty,
  children
}: {
  title: string;
  count: number;
  empty?: string;
  children: ReactNode;
}) {
  return (
    <section className="space-y-3" aria-label={title}>
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-bold text-primary">{title}</h2>
        <span className="rounded-full bg-brand-subtle px-2.5 py-0.5 text-xs font-semibold text-brand-primary">
          {count}
        </span>
      </div>
      {count === 0 && empty ? (
        <SurfaceCard className="p-6 text-center">
          <Package className="mx-auto h-8 w-8 text-muted" />
          <p className="mt-2 text-sm font-medium text-primary">{empty}</p>
        </SurfaceCard>
      ) : (
        <div className="space-y-2">{children}</div>
      )}
    </section>
  );
}

function JobRow({ job, onOpen }: { job: DeliverySessionResponse; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="group flex min-h-[56px] w-full items-center justify-between rounded-xl border border-border-subtle bg-card px-4 py-2 text-left shadow-sm transition-all hover:border-brand-primary hover:bg-canvas active:scale-[0.99] touch-manipulation"
    >
      <span className="flex min-w-0 items-center gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary">
          <Package className="h-5 w-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2">
            <span className="truncate font-mono text-sm font-bold tabular-nums text-primary">
              {job.trackingNumber}
            </span>
            <FsmStatusBadge state={job.currentState || 'ASSIGNED'} density="touch" size="sm" />
          </span>
          <span className="flex items-center gap-1.5 truncate text-xs text-secondary">
            <span>{districtLabel(job.pickupDistrict)}</span>
            <span>&rarr;</span>
            <span className="font-medium text-primary">{districtLabel(job.deliveryDistrict)}</span>
          </span>
        </span>
      </span>
      <ChevronRight className="h-5 w-5 shrink-0 text-muted transition-colors group-hover:text-brand-primary" />
    </button>
  );
}
