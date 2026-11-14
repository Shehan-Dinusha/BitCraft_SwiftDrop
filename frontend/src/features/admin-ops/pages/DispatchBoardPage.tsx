import { useCallback, useEffect, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import { AlertTriangle, Bike, Package, RefreshCw, Search, UserCheck } from 'lucide-react';
import { Button, EmptyState, StatusBanner, SurfaceCard } from '@/design-system';
import { Input } from '@/design-system/primitives/Input';
import {
  dispatchApi,
  type CourierShiftResponse,
  type CourierWorkloadResponse,
  type DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { ManualDispatchOverrideModal } from '../components/ManualDispatchOverrideModal';
import { LiveFleetHeatmap } from '../components/LiveFleetHeatmap';

function getErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    return data?.message ?? data?.error ?? fallback;
  }
  return error instanceof Error ? error.message : fallback;
}

export function DispatchBoardPage() {
  const [unassigned, setUnassigned] = useState<DispatchAssignmentResponse[]>([]);
  const [assignments, setAssignments] = useState<DispatchAssignmentResponse[]>([]);
  const [couriers, setCouriers] = useState<CourierShiftResponse[]>([]);
  const [workloads, setWorkloads] = useState<CourierWorkloadResponse[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<DispatchAssignmentResponse | null>(null);
  const [suggestions, setSuggestions] = useState<
    Awaited<ReturnType<typeof dispatchApi.getCourierSuggestions>>
  >([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [unassignedOrders, courierList, workloadList, assignmentPage] = await Promise.all([
        dispatchApi.getUnassignedParcels(),
        dispatchApi.getCouriers(),
        dispatchApi.getCourierWorkloads(),
        dispatchApi.getAssignments({ status: 'ASSIGNED', page: 0, size: 100 })
      ]);
      setUnassigned(unassignedOrders);
      setCouriers(courierList);
      setWorkloads(workloadList);
      setAssignments(assignmentPage.content);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, 'Dispatch data could not be loaded.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openAssignment = async (order: DispatchAssignmentResponse) => {
    setSelectedOrder(order);
    setSuggestions([]);
    if (!order.orderId) return;
    setLoadingSuggestions(true);
    try {
      setSuggestions(await dispatchApi.getCourierSuggestions(order.orderId));
    } catch (error) {
      setErrorMessage(getErrorMessage(error, 'Courier suggestions could not be loaded.'));
    } finally {
      setLoadingSuggestions(false);
    }
  };

  const handleAssign = async (courierId: string, reason: string) => {
    if (!selectedOrder?.orderId) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await dispatchApi.manualAssign({
        orderId: selectedOrder.orderId,
        courierId,
        assignedBy: 'DISPATCHER',
        reason
      });
      setSelectedOrder(null);
      setSuccessMessage(
        `${selectedOrder.trackingNumber ?? 'Order'} was assigned. The queue and workload have been refreshed.`
      );
      await loadData();
    } catch (error) {
      const conflict = isAxiosError(error) && error.response?.status === 409;
      setErrorMessage(
        conflict
          ? 'Assignment conflict: this order or courier changed in another dispatch session. Refresh and try again.'
          : getErrorMessage(error, 'Courier assignment failed.')
      );
    } finally {
      setSubmitting(false);
    }
  };

  const filteredUnassigned = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return unassigned;
    return unassigned.filter((order) =>
      [order.trackingNumber, order.pickupDistrict, order.deliveryDistrict, order.orderSpeed]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalized))
    );
  }, [query, unassigned]);

  const availableCount = couriers.filter((courier) => courier.shiftStatus === 'AVAILABLE').length;
  const totalActive = workloads.reduce((sum, workload) => sum + (workload.activeParcels ?? 0), 0);

  return (
    <main className="mx-auto max-w-[1600px] space-y-5 p-4 md:p-6" data-density="compact">
      <header className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
        <div>
          <p className="text-overline font-semibold uppercase tracking-wide text-brand-primary">
            Admin operations
          </p>
          <h1 className="text-2xl font-bold tracking-tight text-primary">Dispatch console</h1>
          <p className="text-caption text-secondary">
            Allocate parcels across Sri Lanka with district-aware courier visibility.
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadData()} disabled={loading}>
          <RefreshCw className={`mr-1.5 size-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </header>

      {errorMessage ? (
        <StatusBanner
          tone="danger"
          title="Dispatch action failed"
          onDismiss={() => setErrorMessage(null)}
        >
          {errorMessage}
        </StatusBanner>
      ) : null}
      {successMessage ? (
        <StatusBanner
          tone="success"
          title="Dispatch updated"
          onDismiss={() => setSuccessMessage(null)}
        >
          {successMessage}
        </StatusBanner>
      ) : null}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SurfaceCard className="p-3">
          <p className="text-caption text-secondary">Unassigned orders</p>
          <p className="mt-1 text-2xl font-bold text-status-danger">{unassigned.length}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <p className="text-caption text-secondary">Available couriers</p>
          <p className="mt-1 text-2xl font-bold text-status-success">{availableCount}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <p className="text-caption text-secondary">Active parcels</p>
          <p className="mt-1 text-2xl font-bold text-primary">{totalActive}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <p className="text-caption text-secondary">Assigned orders shown</p>
          <p className="mt-1 text-2xl font-bold text-primary">{assignments.length}</p>
        </SurfaceCard>
      </div>

      <LiveFleetHeatmap couriers={couriers} />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <SurfaceCard className="p-4">
          <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
            <div className="flex items-center gap-2">
              <Package className="size-4 text-status-danger" />
              <h2 className="text-label font-semibold text-primary">Unassigned order queue</h2>
            </div>
            <Input
              variant="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onClear={() => setQuery('')}
              placeholder="Search tracking or district"
              aria-label="Search unassigned orders"
              className="sm:max-w-64"
            />
          </div>
          <div className="mt-4 overflow-x-auto">
            {loading ? (
              <p className="py-10 text-center text-caption text-muted">
                Loading unassigned orders...
              </p>
            ) : filteredUnassigned.length === 0 ? (
              <EmptyState type={query ? 'no-results' : 'empty-queue'} className="mx-auto" />
            ) : (
              <table className="w-full text-left text-caption">
                <thead className="border-b border-border-subtle text-overline uppercase text-secondary">
                  <tr>
                    <th className="px-2 py-2">Order</th>
                    <th className="px-2 py-2">Route</th>
                    <th className="px-2 py-2">Status</th>
                    <th className="px-2 py-2 text-right">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUnassigned.map((order) => (
                    <tr
                      key={order.assignmentId ?? order.orderId}
                      className="border-b border-border-subtle"
                    >
                      <td className="px-2 py-3">
                        <p className="font-mono font-semibold text-primary">
                          {order.trackingNumber ?? order.orderId}
                        </p>
                        <p className="text-muted">
                          {order.orderSpeed ?? 'STANDARD'} · retry {order.retryCount ?? 0}
                        </p>
                      </td>
                      <td className="px-2 py-3 text-secondary">
                        {order.pickupDistrict ?? 'Unknown'} to {order.deliveryDistrict ?? 'Unknown'}
                      </td>
                      <td className="px-2 py-3">
                        <span className="rounded-full bg-status-danger-bg px-2 py-1 text-[10px] font-semibold uppercase text-status-danger-fg">
                          {order.status ?? 'UNASSIGNED'}
                        </span>
                      </td>
                      <td className="px-2 py-3 text-right">
                        <Button variant="primary" onClick={() => void openAssignment(order)}>
                          <UserCheck className="mr-1.5 size-4" />
                          Assign
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </SurfaceCard>

        <SurfaceCard className="p-4">
          <div className="flex items-center gap-2">
            <Bike className="size-4 text-brand-primary" />
            <h2 className="text-label font-semibold text-primary">Courier workload</h2>
          </div>
          <div className="mt-4 space-y-2">
            {workloads.length === 0 ? (
              <p className="py-8 text-center text-caption text-muted">
                No workload data available.
              </p>
            ) : (
              workloads.map((workload) => {
                const active = workload.activeParcels ?? 0;
                const capacity = workload.maxCapacity ?? 0;
                const percentage = capacity
                  ? Math.min(100, Math.round((active / capacity) * 100))
                  : 0;
                return (
                  <div
                    key={workload.courierId}
                    className="rounded-lg border border-border-subtle p-3"
                  >
                    <div className="flex items-center justify-between gap-2 text-caption">
                      <span className="font-semibold text-primary">
                        {workload.courierName ?? workload.courierId}
                      </span>
                      <span className="text-secondary">
                        {workload.currentDistrict ?? 'Unknown district'}
                      </span>
                    </div>
                    <div className="mt-2 flex items-center gap-2">
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-canvas">
                        <div
                          className={
                            percentage >= 80
                              ? 'h-full bg-status-warning-fg'
                              : 'h-full bg-status-success-fg'
                          }
                          style={{ width: `${percentage}%` }}
                        />
                      </div>
                      <span className="w-16 text-right text-caption text-secondary">
                        {active}/{capacity}
                      </span>
                    </div>
                    <p className="mt-1 text-overline uppercase text-secondary">
                      {workload.shiftStatus ?? 'UNKNOWN'}
                    </p>
                  </div>
                );
              })
            )}
          </div>
        </SurfaceCard>
      </div>

      <SurfaceCard className="p-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Search className="size-4 text-secondary" />
            <h2 className="text-label font-semibold text-primary">Assigned orders</h2>
          </div>
          <span className="text-caption text-secondary">
            Select an order to reassign when permitted.
          </span>
        </div>
        {assignments.length === 0 ? (
          <p className="py-8 text-center text-caption text-muted">No assigned orders returned.</p>
        ) : (
          <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
            {assignments.map((assignment) => (
              <button
                type="button"
                key={assignment.assignmentId ?? assignment.orderId}
                onClick={() => void openAssignment(assignment)}
                className="rounded-lg border border-border-subtle p-3 text-left transition-colors hover:bg-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-caption font-semibold text-primary">
                    {assignment.trackingNumber ?? assignment.orderId}
                  </span>
                  <span className="text-overline uppercase text-secondary">
                    {assignment.status}
                  </span>
                </div>
                <p className="mt-1 text-caption text-secondary">
                  {assignment.courierName ?? 'Unassigned'} ·{' '}
                  {assignment.pickupDistrict ?? 'Unknown district'}
                </p>
              </button>
            ))}
          </div>
        )}
      </SurfaceCard>

      <ManualDispatchOverrideModal
        open={Boolean(selectedOrder)}
        onClose={() => setSelectedOrder(null)}
        order={selectedOrder}
        couriers={couriers}
        suggestions={suggestions}
        loadingSuggestions={loadingSuggestions}
        submitting={submitting}
        onSubmit={handleAssign}
      />
      {errorMessage?.includes('conflict') ? (
        <div className="sr-only">
          <AlertTriangle aria-hidden="true" />
        </div>
      ) : null}
    </main>
  );
}

export default DispatchBoardPage;
