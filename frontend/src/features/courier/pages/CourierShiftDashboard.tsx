import { useState, useEffect, useCallback } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import {
  dispatchApi,
  type CourierShiftResponse,
  type DispatchAssignmentResponse,
  type ShiftStatus,
  type SriLankanDistrict
} from '@/services/api/dispatchApi';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import {
  Bike,
  Coffee,
  MapPin,
  Package,
  Power,
  RefreshCw,
  Send,
  ShieldCheck,
  CheckCircle2,
  Clock
} from 'lucide-react';

const SRI_LANKAN_DISTRICTS: SriLankanDistrict[] = [
  'COLOMBO',
  'GAMPAHA',
  'KALUTARA',
  'KANDY',
  'MATALE',
  'NUWARA_ELIYA',
  'GALLE',
  'MATARA',
  'HAMBANTOTA',
  'JAFFNA',
  'KURUNEGALA',
  'RATNAPURA'
];

export function CourierShiftDashboard() {
  const user = useAuthStore((s) => s.user);
  const courierId = user?.id || '11111111-1111-1111-1111-111111111111';

  const [shift, setShift] = useState<CourierShiftResponse | null>(null);
  const [assignments, setAssignments] = useState<DispatchAssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Start shift form state
  const [selectedDistrict, setSelectedDistrict] = useState<SriLankanDistrict>(
    (user?.district as SriLankanDistrict) || 'COLOMBO'
  );

  const fetchShiftData = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const shiftData = await dispatchApi.getCourier(courierId);
      setShift(shiftData);

      const assignedOrders = await dispatchApi.getCourierAssignments(courierId, 'ASSIGNED');
      setAssignments(assignedOrders);
    } catch {
      // If courier not registered yet, leave shift as null so they can start
      setShift(null);
    } finally {
      setLoading(false);
    }
  }, [courierId]);

  useEffect(() => {
    fetchShiftData();
  }, [fetchShiftData]);

  const handleStartShift = async () => {
    try {
      setUpdating(true);
      setErrorMsg(null);
      const res = await dispatchApi.startShift({
        courierId,
        courierName: user?.username || 'Sunil Perera',
        phone: '+94771234567',
        vehicleType: 'MOTORCYCLE',
        initialDistrict: selectedDistrict,
        maxCapacity: 5
      });
      setShift(res);
      setSuccessMsg('Shift started! You are now AVAILABLE for delivery dispatches.');
      await fetchShiftData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to start shift';
      setErrorMsg(msg);
    } finally {
      setUpdating(false);
    }
  };

  const handleUpdateStatus = async (newStatus: ShiftStatus) => {
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

  const handleLocationPing = async () => {
    try {
      setUpdating(true);
      setErrorMsg(null);
      const res = await dispatchApi.updateLocation({
        courierId,
        district: shift?.currentDistrict || selectedDistrict,
        latitude: 6.9271,
        longitude: 79.8612
      });
      setShift(res);
      setSuccessMsg('GPS location heartbeat sent successfully!');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to send location heartbeat';
      setErrorMsg(msg);
    } finally {
      setUpdating(false);
    }
  };

  const handleEndShift = async () => {
    if (!window.confirm('Are you sure you want to end your shift?')) return;
    try {
      setUpdating(true);
      setErrorMsg(null);
      const res = await dispatchApi.endShift(courierId);
      setShift(res);
      setSuccessMsg('Shift ended. Good work today!');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to end shift';
      setErrorMsg(msg);
    } finally {
      setUpdating(false);
    }
  };

  const isOnline = shift && shift.shiftStatus !== 'OFFLINE';
  const capacityUsed = shift ? (shift.activeParcelsCount ?? 0) : 0;
  const maxCap = shift?.maxCapacity || 5;
  const capacityPct = Math.min(100, Math.round((capacityUsed / maxCap) * 100));

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-4 md:p-6" data-density="touch">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Courier Shift Dashboard
          </h1>
          <p className="text-sm text-slate-500">
            Real-time shift controls, vehicle capacity, and live dispatch assignments.
          </p>
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
              ? 'border-l-emerald-500 bg-emerald-500/5'
              : 'border-l-amber-500 bg-amber-500/5'
            : 'border-l-slate-400 bg-slate-500/5'
        }`}
      >
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-12 w-12 items-center justify-center rounded-xl text-white ${
                isOnline
                  ? shift?.shiftStatus === 'AVAILABLE'
                    ? 'bg-emerald-600'
                    : 'bg-amber-500'
                  : 'bg-slate-500'
              }`}
            >
              <Bike className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-slate-900">
                  {user?.username || shift?.courierName || 'Courier'}
                </span>
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    isOnline
                      ? shift?.shiftStatus === 'AVAILABLE'
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-amber-100 text-amber-800'
                      : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {shift?.shiftStatus || 'OFFLINE'}
                </span>
              </div>
              <p className="flex items-center gap-1.5 text-xs text-slate-500">
                <MapPin className="h-3.5 w-3.5 text-indigo-500" />
                Operating District: <strong>{shift?.currentDistrict || selectedDistrict}</strong>
              </p>
            </div>
          </div>

          {/* Touch-Friendly Action Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            {!isOnline ? (
              <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
                <select
                  value={selectedDistrict}
                  onChange={(e) => setSelectedDistrict(e.target.value as SriLankanDistrict)}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                >
                  {SRI_LANKAN_DISTRICTS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
                <Button
                  variant="primary"
                  onClick={handleStartShift}
                  disabled={updating}
                  className="min-h-[48px] px-6 font-semibold shadow-md"
                >
                  <Power className="mr-2 h-5 w-5" />
                  Start Shift
                </Button>
              </div>
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
                  variant="outline"
                  onClick={handleLocationPing}
                  disabled={updating}
                  className="min-h-[44px]"
                  title="Ping live GPS heartbeat"
                >
                  <Send className="mr-1.5 h-4 w-4 text-indigo-500" />
                  Ping GPS
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleEndShift}
                  disabled={updating || capacityUsed > 0}
                  className="min-h-[44px]"
                  title={capacityUsed > 0 ? 'Deliver active parcels before ending shift' : 'End Shift'}
                >
                  <Power className="mr-1.5 h-4 w-4" />
                  End Shift
                </Button>
              </>
            )}
          </div>
        </div>
      </SurfaceCard>

      {/* Capacity & Shift Metrics */}
      {isOnline && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500">Parcels on Board</span>
              <Package className="h-4 w-4 text-indigo-500" />
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-900">
                {capacityUsed}
              </span>
              <span className="text-xs text-slate-500">/ {maxCap} max</span>
            </div>
            <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  capacityPct >= 80 ? 'bg-amber-500' : 'bg-indigo-600'
                }`}
                style={{ width: `${capacityPct}%` }}
              />
            </div>
          </SurfaceCard>

          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500">Vehicle Type</span>
              <Bike className="h-4 w-4 text-emerald-500" />
            </div>
            <div className="mt-2 text-xl font-bold text-slate-900">
              {shift?.vehicleType || 'MOTORCYCLE'}
            </div>
            <p className="mt-1 text-xs text-slate-500">Optimized for urban rapid delivery</p>
          </SurfaceCard>

          <SurfaceCard className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500">Shift Started</span>
              <Clock className="h-4 w-4 text-cyan-500" />
            </div>
            <div className="mt-2 text-sm font-semibold text-slate-900">
              {shift?.shiftStartedAt
                ? new Date(shift.shiftStartedAt).toLocaleTimeString([], {
                    hour: '2-digit',
                    minute: '2-digit'
                  })
                : 'Not started'}
            </div>
            <p className="mt-1 text-xs text-slate-500">Last Ping: Just now</p>
          </SurfaceCard>
        </div>
      )}

      {/* Active Dispatched Assignments Queue */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900">
            Active Assigned Orders ({assignments.length})
          </h2>
          <span className="text-xs text-slate-500">Auto-assigned by Dispatch Engine</span>
        </div>

        {assignments.length === 0 ? (
          <SurfaceCard className="p-8 text-center">
            <ShieldCheck className="mx-auto h-10 w-10 text-emerald-500/80" />
            <h3 className="mt-3 font-semibold text-slate-900">
              No Pending Pickups
            </h3>
            <p className="mt-1 text-sm text-slate-500">
              {isOnline
                ? 'You are active and ready. Orders will appear here as soon as customers book in your district.'
                : 'Start your shift above to receive automated dispatch assignments.'}
            </p>
          </SurfaceCard>
        ) : (
          <div className="space-y-3">
            {assignments.map((item) => (
              <SurfaceCard
                key={item.assignmentId}
                className="flex flex-col justify-between gap-4 p-4 sm:flex-row sm:items-center"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-bold text-indigo-600">
                      {item.trackingNumber || 'UNTRACKED'}
                    </span>
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                      {item.orderSpeed}
                    </span>
                    <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                      {item.status}
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                    <span>
                      Pickup: <strong>{item.pickupDistrict}</strong>
                    </span>
                    <span>?</span>
                    <span>
                      Delivery: <strong>{item.deliveryDistrict}</strong>
                    </span>
                    <span>
                      ? Est. pickup:{' '}
                      <strong className="text-slate-700">
                        {item.estimatedPickupMinutes ?? 25} mins
                      </strong>
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="primary"
                    className="min-h-[44px] min-w-[120px]"
                    onClick={() => {
                      window.location.href = `/courier/deliveries?orderId=${item.orderId}`;
                    }}
                  >
                    Execute Pickup
                  </Button>
                </div>
              </SurfaceCard>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
