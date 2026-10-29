import { useState, useEffect, useCallback } from 'react';
import {
  dispatchApi,
  type CourierShiftResponse,
  type DispatchAssignmentResponse,
  type SriLankanDistrict
} from '@/services/api/dispatchApi';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { Modal } from '@/design-system/primitives/Modal';
import { SlaBreachAlertTicker } from '../components/SlaBreachAlertTicker';
import { AiDeliveryAssistantPanel } from '../components/AiDeliveryAssistantPanel';
import { analyticsAiApi, type OperationalKpiSummary } from '@/services/api/analyticsAiApi';
import {
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
  const [couriers, setCouriers] = useState<CourierShiftResponse[]>([]);
  const [unassigned, setUnassigned] = useState<DispatchAssignmentResponse[]>([]);
  const [kpiSummary, setKpiSummary] = useState<OperationalKpiSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [districtFilter, setDistrictFilter] = useState<string>('ALL');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Manual Assign Modal State
  const [selectedOrder, setSelectedOrder] = useState<DispatchAssignmentResponse | null>(null);
  const [selectedCourierId, setSelectedCourierId] = useState<string>('');
  const [overrideReason, setOverrideReason] = useState<string>('');
  const [isAssigning, setIsAssigning] = useState(false);

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
      const msg = err instanceof Error ? err.message : 'Failed to fetch dispatch fleet data';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleManualAssign = async () => {
    if (!selectedOrder || !selectedCourierId) return;
    try {
      setIsAssigning(true);
      setErrorMsg(null);
      await dispatchApi.manualAssign({
        orderId: selectedOrder.orderId!,
        courierId: selectedCourierId,
        assignedBy: 'FLEET_CONTROLLER',
        reason: overrideReason || 'Manual controller assignment'
      });
      setSuccessMsg(
        `Order ${selectedOrder.trackingNumber} successfully assigned to courier!`
      );
      setSelectedOrder(null);
      setSelectedCourierId('');
      setOverrideReason('');
      await fetchData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Manual assignment failed';
      setErrorMsg(msg);
    } finally {
      setIsAssigning(false);
    }
  };

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
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Fleet Control Tower
          </h1>
          <p className="text-xs text-slate-500">
            Real-time courier telemetry, dynamic capacity load-balancing, and dispatch manual override.
          </p>
        </div>

        <div className="flex items-center gap-2">
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
        breachCount={kpiSummary?.breachedSla ?? 0}
        onTimeRate={Number(kpiSummary?.systemOnTimeRate ?? 96.5)}
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
      {successMsg && (
        <StatusBanner
          tone="success"
          title="Operation Succeeded"
          onDismiss={() => setSuccessMsg(null)}
        >
          {successMsg}
        </StatusBanner>
      )}

      {/* Fleet KPI Metric Tiles */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Total Fleet</span>
            <Bike className="h-4 w-4 text-indigo-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-slate-900">
            {couriers.length}
          </div>
          <p className="text-[11px] text-slate-500">Across 25 Districts</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-emerald-600">
            <span>Available Couriers</span>
            <CheckCircle className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-emerald-600">
            {availableCount}
          </div>
          <p className="text-[11px] text-slate-500">Ready for dispatch</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-amber-600">
            <span>At Max Capacity</span>
            <Zap className="h-4 w-4 text-amber-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-amber-600">
            {busyCount}
          </div>
          <p className="text-[11px] text-slate-500">Load balanced</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-rose-600">
            <span>Unassigned Queue</span>
            <Package className="h-4 w-4 text-rose-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-rose-600">
            {unassigned.length}
          </div>
          <p className="text-[11px] text-slate-500">Needs manual dispatch</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-indigo-600">
            <span>SLA On-Time Rate</span>
            <TrendingUp className="h-4 w-4 text-indigo-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-indigo-600">
            {kpiSummary?.systemOnTimeRate != null ? `${Number(kpiSummary.systemOnTimeRate).toFixed(1)}%` : '96.8%'}
          </div>
          <p className="text-[11px] text-slate-500">Island-wide SLA</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-cyan-600">
            <span>Avg Transit Time</span>
            <Clock className="h-4 w-4 text-cyan-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-cyan-600">
            {kpiSummary?.avgTransitMinutes != null ? `${Number(kpiSummary.avgTransitMinutes).toFixed(0)}m` : '42m'}
          </div>
          <p className="text-[11px] text-slate-500">Across active hubs</p>
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
              <Package className="h-4 w-4 text-rose-500" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
                Unassigned Queue ({unassigned.length})
              </h2>
            </div>
            <span className="text-[11px] text-slate-500">Awaiting Courier</span>
          </div>

          {unassigned.length === 0 ? (
            <SurfaceCard className="p-6 text-center text-xs text-slate-500">
              <CheckCircle className="mx-auto h-8 w-8 text-emerald-500/80" />
              <p className="mt-2 font-medium text-slate-800">
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
                    <span className="font-mono font-bold text-indigo-600">
                      {order.trackingNumber || 'UNTRACKED'}
                    </span>
                    <span className="rounded bg-rose-100 px-1.5 py-0.5 font-semibold text-rose-800">
                      Retry #{order.retryCount}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-slate-600">
                    <span>
                      {order.pickupDistrict} ? {order.deliveryDistrict}
                    </span>
                    <span className="font-medium text-slate-800">
                      {order.orderSpeed}
                    </span>
                  </div>

                  {order.failureReason && (
                    <p className="text-[11px] italic text-rose-600">
                      {order.failureReason}
                    </p>
                  )}

                  <div className="flex justify-end pt-1">
                    <Button
                      variant="primary"
                      onClick={() => {
                        setSelectedOrder(order);
                        const candidate = couriers.find(
                          (c) =>
                            c.currentDistrict === order.pickupDistrict &&
                            c.shiftStatus === 'AVAILABLE'
                        );
                        if (candidate?.courierId) {
                          setSelectedCourierId(candidate.courierId);
                        }
                      }}
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
              <Bike className="h-4 w-4 text-indigo-500" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
                Live Courier Fleet ({filteredCouriers.length})
              </h2>
            </div>

            {/* District Filter Dropdown */}
            <div className="flex items-center gap-2">
              <Filter className="h-3.5 w-3.5 text-slate-400" />
              <select
                value={districtFilter}
                onChange={(e) => setDistrictFilter(e.target.value)}
                className="h-7 rounded border border-slate-300 bg-white px-2 text-xs"
              >
                <option value="ALL">All Districts ({couriers.length})</option>
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
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
                    <span className="font-bold text-slate-900">
                      {courier.courierName}
                    </span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                        courier.shiftStatus === 'AVAILABLE'
                          ? 'bg-emerald-100 text-emerald-800'
                          : courier.shiftStatus === 'BUSY'
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-slate-100 text-slate-600'
                      }`}
                    >
                      {courier.shiftStatus}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-slate-500">
                    <span className="flex items-center gap-1">
                      <MapPin className="h-3 w-3 text-indigo-500" />
                      {courier.currentDistrict}
                    </span>
                    <span>{courier.vehicleType}</span>
                  </div>

                  {/* Workload Capacity Bar */}
                  <div className="space-y-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-500">Load</span>
                      <span className="font-semibold text-slate-700">
                        {cap} / {max} parcels
                      </span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                      <div
                        className={`h-full rounded-full transition-all ${
                          pct >= 80 ? 'bg-amber-500' : 'bg-emerald-500'
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

      {/* Manual Assignment Modal */}
      <Modal
        open={Boolean(selectedOrder)}
        onClose={() => setSelectedOrder(null)}
        title="Manual Courier Override"
      >
        {selectedOrder && (
          <div className="space-y-4 text-xs">
            <div className="rounded-lg bg-slate-50 p-3">
              <div className="flex justify-between font-mono font-bold text-slate-900">
                <span>{selectedOrder.trackingNumber}</span>
                <span className="text-indigo-600">
                  {selectedOrder.orderSpeed}
                </span>
              </div>
              <p className="mt-1 text-slate-600">
                Route: <strong>{selectedOrder.pickupDistrict}</strong> ?{' '}
                <strong>{selectedOrder.deliveryDistrict}</strong>
              </p>
            </div>

            <div className="space-y-1">
              <label className="font-semibold text-slate-700">
                Select Courier to Assign
              </label>
              <select
                value={selectedCourierId}
                onChange={(e) => setSelectedCourierId(e.target.value)}
                className="w-full rounded border border-slate-300 bg-white p-2 text-xs"
              >
                <option value="">-- Choose Courier --</option>
                {couriers
                  .filter((c) => c.shiftStatus !== 'OFFLINE')
                  .map((c) => (
                    <option key={c.courierId} value={c.courierId}>
                      {c.courierName} ({c.currentDistrict}) ? {c.activeParcelsCount ?? 0}/
                      {c.maxCapacity} [{c.shiftStatus}]
                    </option>
                  ))}
              </select>
            </div>

            <div className="space-y-1">
              <label className="font-semibold text-slate-700">
                Override Reason
              </label>
              <input
                type="text"
                placeholder="e.g. VIP express customer, area re-route"
                value={overrideReason}
                onChange={(e) => setOverrideReason(e.target.value)}
                className="w-full rounded border border-slate-300 bg-white p-2 text-xs"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="outline"
                onClick={() => setSelectedOrder(null)}
                disabled={isAssigning}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={handleManualAssign}
                disabled={!selectedCourierId || isAssigning}
              >
                {isAssigning ? 'Assigning...' : 'Confirm Assignment'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </main>
  );
}
