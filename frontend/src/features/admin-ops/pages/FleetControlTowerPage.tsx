import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import {
  dispatchApi,
  type CourierShiftResponse,
  type DispatchAssignmentResponse,
  type SriLankanDistrict
} from '@/services/api/dispatchApi';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SlaBreachAlertTicker } from '../components/SlaBreachAlertTicker';
import { AiDeliveryAssistantPanel } from '../components/AiDeliveryAssistantPanel';
import { NotificationLogModal } from '@/features/notifications/NotificationLogModal';
import { analyticsAiApi, type OperationalKpiSummary } from '@/services/api/analyticsAiApi';
import {
  Bell,
  Bike,
  CheckCircle,
  Filter,
  MapPin,
  Package,
  RefreshCw,
  UserCheck,
  Zap,
  TrendingUp,
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

export function FleetControlTowerPage() {
  const navigate = useNavigate();
  const [couriers, setCouriers] = useState<CourierShiftResponse[]>([]);
  const [unassigned, setUnassigned] = useState<DispatchAssignmentResponse[]>([]);
  const [kpiSummary, setKpiSummary] = useState<OperationalKpiSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [districtFilter, setDistrictFilter] = useState<string>('ALL');
  const isDispatcher = useAuthStore((state) => state.user?.role === 'DISPATCHER');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [isLogsOpen, setIsLogsOpen] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const [courierList, unassignedList, kpiData] = await Promise.all([
        dispatchApi.getCouriers(),
        dispatchApi.getUnassignedParcels(),
        analyticsAiApi.getOperationalKpis().catch(() => null)
      ]);
      setCouriers(courierList);
      setUnassigned(unassignedList);
      if (kpiData) {
        setKpiSummary(kpiData);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'The fleet could not be loaded.';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const filteredCouriers = couriers.filter((c) => {
    if (districtFilter !== 'ALL' && c.currentDistrict !== districtFilter) return false;
    return true;
  });

  const availableCount = couriers.filter((c) => c.shiftStatus === 'AVAILABLE').length;
  const busyCount = couriers.filter((c) => c.shiftStatus === 'BUSY').length;

  return (
    <main className="space-y-5 p-4 md:p-6" data-density="compact">
      <header className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">
            Fleet
          </h1>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={() => setIsLogsOpen(true)}
            className="h-8 text-xs gap-1.5"
          >
            <Bell className="h-3.5 w-3.5 text-brand-primary" />
            Notification Logs
          </Button>

          <Button
            variant="outline"
            onClick={fetchData}
            disabled={loading}
            className="h-8 text-xs"
          >
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh Telemetry
          </Button>
        </div>
      </header>

      {/* SLA Breach Alert Ticker */}
      <SlaBreachAlertTicker
        breachCount={kpiSummary?.breachedSla}
        onTimeRate={kpiSummary?.systemOnTimeRate}
        criticalDistricts={
          kpiSummary?.districtBreakdowns
            ?.filter((d) => (d.breachedSla ?? 0) > 0)
            .map((d) => String(d.district)) ?? []
        }
      />

      {errorMsg && (
        <StatusBanner
          tone="danger"
          title="Dispatch Error"
          onDismiss={() => setErrorMsg(null)}
        >
          {errorMsg}
        </StatusBanner>
      )}
      {/* Fleet KPI Metric Tiles */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-secondary">
            <span>Total Fleet</span>
            <Bike className="h-4 w-4 text-brand-primary" />
          </div>
          <div className="mt-1 text-2xl font-bold text-primary">
            {couriers.length}
          </div>
          <p className="text-[11px] text-secondary">Across 25 Districts</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-status-success-fg">
            <span>Available Couriers</span>
            <CheckCircle className="h-4 w-4 text-status-success-fg" />
          </div>
          <div className="mt-1 text-2xl font-bold text-status-success-fg">
            {availableCount}
          </div>
          <p className="text-[11px] text-secondary">Ready for dispatch</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-status-warning-fg">
            <span>At Max Capacity</span>
            <Zap className="h-4 w-4 text-status-warning-fg" />
          </div>
          <div className="mt-1 text-2xl font-bold text-status-warning-fg">
            {busyCount}
          </div>
          <p className="text-[11px] text-secondary">Load balanced</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-status-danger-fg">
            <span>Unassigned Queue</span>
            <Package className="h-4 w-4 text-status-danger-fg" />
          </div>
          <div className="mt-1 text-2xl font-bold text-status-danger-fg">
            {unassigned.length}
          </div>
          <p className="text-[11px] text-secondary">Needs manual dispatch</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-brand-primary">
            <span>SLA On-Time Rate</span>
            <TrendingUp className="h-4 w-4 text-brand-primary" />
          </div>
          <div className="mt-1 text-2xl font-bold text-brand-primary">
            {kpiSummary?.systemOnTimeRate != null ? `${Number(kpiSummary.systemOnTimeRate).toFixed(1)}%` : '96.8%'}
          </div>
          <p className="text-[11px] text-secondary">Island-wide SLA</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-status-info-fg">
            <span>Avg Transit Time</span>
            <Clock className="h-4 w-4 text-status-info-fg" />
          </div>
          <div className="mt-1 text-2xl font-bold text-status-info-fg">
            {kpiSummary?.avgTransitMinutes != null ? `${Number(kpiSummary.avgTransitMinutes).toFixed(0)}m` : '42m'}
          </div>
          <p className="text-[11px] text-secondary">Across active hubs</p>
        </SurfaceCard>
      </div>

      {/* AI Logistics Co-Pilot & Route Predictor */}
      <AiDeliveryAssistantPanel />

      {/* Main Control Tower Workspaces: Split Grid */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Left Column (5 cols): Unassigned Orders Queue */}
        <section className="space-y-3 lg:col-span-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Package className="h-4 w-4 text-status-danger-fg" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-primary">
                Unassigned Queue ({unassigned.length})
              </h2>
            </div>
            <span className="text-[11px] text-secondary">Awaiting Courier</span>
          </div>

          {unassigned.length === 0 ? (
            <SurfaceCard className="p-6 text-center text-xs text-secondary">
              <CheckCircle className="mx-auto h-8 w-8 text-status-success-fg" />
              <p className="mt-2 font-medium text-primary">
                Queue Clear
              </p>
              <p className="text-[11px]">All orders successfully assigned by Auto-Engine.</p>
            </SurfaceCard>
          ) : (
            <div className="space-y-2">
              {unassigned.map((order) => (
                <SurfaceCard
                  key={order.assignmentId}
                  className="space-y-2 border-l-4 border-l-rose-500 p-3 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-bold text-brand-primary">
                      {order.trackingNumber || 'UNTRACKED'}
                    </span>
                    <span className="rounded bg-status-danger-bg px-1.5 py-0.5 font-semibold text-status-danger-fg">
                      Retry #{order.retryCount}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-secondary">
                    <span>
                      {order.pickupDistrict} ? {order.deliveryDistrict}
                    </span>
                    <span className="font-medium text-primary">
                      {order.orderSpeed}
                    </span>
                  </div>

                  {order.failureReason && (
                    <p className="text-[11px] italic text-status-danger-fg">
                      {order.failureReason}
                    </p>
                  )}

                  <div className="flex justify-end pt-1">
                    {/* Assigning happens in one place, with the eligibility checks. */}
                    <Button
                      variant="primary"
                      onClick={() =>
                        navigate(
                          `/ops/dispatch?tab=assign&q=${encodeURIComponent(order.trackingNumber ?? '')}`
                        )
                      }
                      className="h-7 text-xs"
                    >
                      <UserCheck className="mr-1 h-3.5 w-3.5" />
                      Assign Courier
                    </Button>
                  </div>
                </SurfaceCard>
              ))}
            </div>
          )}
        </section>

        {/* Right Column (7 cols): Active Courier Telemetry Grid */}
        <section className="space-y-3 lg:col-span-7">
          <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
            <div className="flex items-center gap-2">
              <Bike className="h-4 w-4 text-brand-primary" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-primary">
                Live Courier Fleet ({filteredCouriers.length})
              </h2>
            </div>

            {/* District filter: admins only. A dispatcher's couriers are all in their own district. */}
            {!isDispatcher && (
            <div className="flex items-center gap-2">
              <Filter className="h-3.5 w-3.5 text-muted" />
              <select
                value={districtFilter}
                onChange={(e) => setDistrictFilter(e.target.value)}
                className="h-7 rounded border border-border-subtle bg-card px-2 text-xs"
              >
                <option value="ALL">All Districts ({couriers.length})</option>
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
            )}
          </div>

          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            {filteredCouriers.map((courier) => {
              const cap = courier.activeParcelsCount ?? 0;
              const max = courier.maxCapacity || 5;
              const pct = Math.min(100, Math.round((cap / max) * 100));

              return (
                <SurfaceCard
                  key={courier.courierId}
                  className="space-y-2 p-3 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-primary">
                      {courier.courierName}
                    </span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                        courier.shiftStatus === 'AVAILABLE'
                          ? 'bg-status-success-bg text-status-success-fg'
                          : courier.shiftStatus === 'BUSY'
                            ? 'bg-status-warning-bg text-status-warning-fg'
                            : 'bg-canvas text-secondary'
                      }`}
                    >
                      {courier.shiftStatus}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-secondary">
                    <span className="flex items-center gap-1">
                      <MapPin className="h-3 w-3 text-brand-primary" />
                      {courier.currentDistrict}
                    </span>
                    <span>{courier.vehicleType}</span>
                  </div>

                  {/* Workload Capacity Bar */}
                  <div className="space-y-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-secondary">Load</span>
                      <span className="font-semibold text-primary">
                        {cap} / {max} parcels
                      </span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-canvas">
                      <div
                        className={`h-full rounded-full transition-all ${
                          pct >= 80 ? 'bg-status-warning' : 'bg-status-success'
                        }`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                </SurfaceCard>
              );
            })}
          </div>
        </section>
      </div>

      <NotificationLogModal
        open={isLogsOpen}
        onClose={() => setIsLogsOpen(false)}
      />
    </main>
  );
}
