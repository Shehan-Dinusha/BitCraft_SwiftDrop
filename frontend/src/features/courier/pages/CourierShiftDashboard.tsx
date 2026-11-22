import { useState, useEffect, useCallback } from 'react';
import { phoneDigits, phoneProblem, toE164 } from '@/utils/phone';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import {
  dispatchApi,
  type CourierProfileResponse,
  type CourierShiftResponse,
  type ShiftStatus
} from '@/services/api/dispatchApi';
import { deliveryApi, type DeliverySessionResponse } from '@/services/api/deliveryApi';
import { fetchCurrentUser } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { isOpenForCourier } from '@/types/fsm';
import { lkr } from '@/features/cash/cashDisplay';
import { cashApi } from '@/services/api/cashApi';
import { vehicleLabel } from '@/utils/vehicles';
import { districtLabel } from '@/features/profile/profileFormat';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import {
  Bike,
  Coffee,
  MapPin,
  Package,
  Power,
  RefreshCw,
  CheckCircle2,
  Clock,
  ChevronRight
} from 'lucide-react';

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
      setSuccessMsg(`Status updated to ${newStatus}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update shift status';
      setErrorMsg(msg);
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

  const openJobs = todayDeliveries.filter((job) => isOpenForCourier(job.currentState));
  const isOnline = shift && shift.shiftStatus !== 'OFFLINE';
  const capacityUsed = shift ? (shift.activeParcelsCount ?? 0) : 0;
  const maxCap = shift?.maxCapacity || 5;
  const capacityPct = Math.min(100, Math.round((capacityUsed / maxCap) * 100));

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-4 md:p-6" data-density="touch">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">
            My shift
          </h1>
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
        <StatusBanner
          tone="danger"
          title="Dispatch Notice"
          onDismiss={() => setErrorMsg(null)}
        >
          {errorMsg}
        </StatusBanner>
      )}
      {successMsg && (
        <StatusBanner
          tone="success"
          title="Success"
          onDismiss={() => setSuccessMsg(null)}
        >
          {successMsg}
        </StatusBanner>
      )}

      {/* Online / Offline Status Banner Card */}
      <SurfaceCard
        variant="elevated"
        className={`border-l-4 p-5 ${
          isOnline
            ? shift?.shiftStatus === 'AVAILABLE'
              ? 'border-l-emerald-500 bg-status-success-bg'
              : 'border-l-amber-500 bg-status-warning-bg'
            : 'border-l-slate-400 bg-canvas'
        }`}
      >
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-12 w-12 items-center justify-center rounded-xl text-brand-on ${
                isOnline
                  ? shift?.shiftStatus === 'AVAILABLE'
                    ? 'bg-status-success'
                    : 'bg-status-warning'
                  : 'bg-muted'
              }`}
            >
              <Bike className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-primary">
                  {user?.username || shift?.courierName || 'Courier'}
                </span>
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    isOnline
                      ? shift?.shiftStatus === 'AVAILABLE'
                        ? 'bg-status-success-bg text-status-success-fg'
                        : 'bg-status-warning-bg text-status-warning-fg'
                      : 'bg-border-subtle text-primary'
                  }`}
                >
                  {shift?.shiftStatus || 'OFFLINE'}
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
              <Button
                variant="primary"
                onClick={handleStartShift}
                disabled={updating}
                className="min-h-target w-full px-6 font-semibold shadow-md sm:w-auto"
              >
                <Power className="mr-2 h-5 w-5" />
                Start shift
              </Button>
            ) : (
              <>
                <Button
                  variant={shift?.shiftStatus === 'AVAILABLE' ? 'primary' : 'outline'}
                  onClick={() => handleUpdateStatus('AVAILABLE')}
                  disabled={updating}
                  className="min-h-[44px]"
                >
                  <CheckCircle2 className="mr-1.5 h-4 w-4" />
                  Available
                </Button>
                <Button
                  variant={shift?.shiftStatus === 'ON_BREAK' ? 'secondary' : 'outline'}
                  onClick={() => handleUpdateStatus('ON_BREAK')}
                  disabled={updating}
                  className="min-h-[44px]"
                >
                  <Coffee className="mr-1.5 h-4 w-4" />
                  Break
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleEndShift}
                  disabled={updating || capacityUsed > 0 || cashInHand > 0}
                  className="min-h-[44px]"
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
              <span className="text-2xl font-bold text-primary">
                {capacityUsed}
              </span>
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

      {/* Stage 14: Today's Deliveries List (/deliveries/me) with Touch Density (56px rows) */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-primary">
              Jobs to do
            </h2>
            <span className="rounded-full bg-brand-subtle px-2.5 py-0.5 text-xs font-semibold text-brand-primary">
              {openJobs.length}
            </span>
          </div>
        </div>

        {openJobs.length === 0 ? (
          <SurfaceCard className="p-6 text-center">
            <Package className="mx-auto h-8 w-8 text-muted" />
            <p className="mt-2 text-sm font-medium text-primary">
              {isOnline ? 'New jobs appear here' : 'Start your shift to get jobs'}
            </p>
          </SurfaceCard>
        ) : (
          <div className="space-y-2">
            {openJobs.map((delivery) => (
              <div
                key={delivery.deliveryId}
                role="button"
                tabIndex={0}
                onClick={() => navigate(`/courier/delivery/${delivery.deliveryId}`)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    navigate(`/courier/delivery/${delivery.deliveryId}`);
                  }
                }}
                className="group flex min-h-[56px] h-14 items-center justify-between rounded-xl border border-border-subtle bg-card px-4 py-2 shadow-sm transition-all hover:border-brand-primary hover:bg-canvas active:scale-[0.99] touch-manipulation cursor-pointer"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary group-hover:bg-brand-subtle">
                    <Package className="h-5 w-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-bold text-primary truncate">
                        {delivery.trackingNumber}
                      </span>
                      <FsmStatusBadge state={delivery.currentState || 'ASSIGNED'} density="touch" size="sm" />
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-secondary truncate">
                      <span>{districtLabel(delivery.pickupDistrict)}</span>
                      <span>&rarr;</span>
                      <span className="font-medium text-primary">{districtLabel(delivery.deliveryDistrict)}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span className="hidden sm:inline text-xs font-medium text-brand-primary group-hover:underline">
                    View
                  </span>
                  <ChevronRight className="h-5 w-5 text-muted group-hover:text-brand-primary transition-colors" />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

    </main>
  );
}
