import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { Button, SelectDropdown, StatusBanner, SurfaceCard, Tabs } from '@/design-system';
import { Input } from '@/design-system/primitives/Input';
import { useAuthStore } from '@/store/useAuthStore';
import { parseApiError } from '@/services/api/apiError';
import { deliveryApi, type DeliveryFailureReason } from '@/services/api/deliveryApi';
import {
  dispatchApi,
  type DispatchAssignmentResponse,
  type SriLankanDistrict
} from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { ManualDispatchOverrideModal } from '../components/ManualDispatchOverrideModal';
import { LiveFleetHeatmap } from '../components/LiveFleetHeatmap';
import { AssignedTab } from '../components/dispatch/AssignedTab';
import { CouriersTab } from '../components/dispatch/CouriersTab';
import { TableSkeleton } from '../components/dispatch/DispatchTable';
import { ALL_DISTRICTS, DISTRICT_OPTIONS } from '../components/dispatch/districtOptions';
import { IncomingTab, ReadyToSendTab } from '../components/dispatch/HubTabs';
import { ReturnsTab, type ReturnStep } from '../components/dispatch/ReturnsTab';
import { ToAssignTab } from '../components/dispatch/ToAssignTab';
import { useDispatchData } from '../components/dispatch/useDispatchData';
import {
  matchesCourier,
  matchesOrder,
  orderKey,
  orderLabel
} from '../components/dispatch/dispatchQueues';

type HubStep = 'IN_TRANSIT' | ReturnStep;
type Notice = { tone: 'success' | 'danger'; title: string; body?: string };

/** Dispatch learns about a hub step from an event, so its lists follow a moment later. */
const EVENT_LAG_MS = 2_000;

const errorText = (error: unknown, fallback: string) =>
  parseApiError(error).message ?? (error instanceof Error ? error.message : fallback);

const stepKey = (order: DispatchAssignmentResponse, step: string) =>
  `${orderKey(order)}:${step}:${order.deliveryAttempts ?? 0}`;

/** One parcel's hub step. A failure is returned so the rest of the batch still runs. */
async function hubStep(
  order: DispatchAssignmentResponse,
  target: HubStep,
  reason?: DeliveryFailureReason
): Promise<string | null> {
  try {
    if (!order.orderId) throw new Error('This parcel has no order ID.');
    const delivery = await deliveryApi.getDeliveryByOrderId(order.orderId);
    if (!delivery.deliveryId) throw new Error('This parcel has no delivery record yet.');
    await deliveryApi.transitionDelivery(delivery.deliveryId, {
      targetState: target,
      failureReason: reason,
      notes: `Dispatcher: ${STEP_DONE[target]}`
    });
    return null;
  } catch (error) {
    return `${orderLabel(order)}: ${errorText(error, 'The hub step could not be saved.')}`;
  }
}

const STEP_DONE: Record<HubStep, string> = {
  IN_TRANSIT: 'marked outbound',
  AT_DESTINATION_HUB: 'accepted at the hub',
  HELD_AT_HUB: 'received at the hub',
  RETURN_IN_TRANSIT: 'sent back',
  RETURN_AT_HUB: 'ready to return',
  FAILED: 'recorded as lost or damaged'
};

function hubNotice(target: HubStep, done: number, failures: string[], retry: boolean): Notice {
  const parcels = `${done} ${done === 1 ? 'parcel' : 'parcels'}`;
  const title = `${parcels} ${retry ? 'ready to go out again' : STEP_DONE[target]}`;
  if (failures.length) {
    return {
      tone: 'danger',
      title: done ? `${title}, ${failures.length} failed` : 'The hub step failed',
      body: failures.join(' ')
    };
  }
  return {
    tone: 'success',
    title,
    body:
      target === 'AT_DESTINATION_HUB' || target === 'RETURN_AT_HUB'
        ? 'Find them under To assign.'
        : undefined
  };
}

/**
 * One tab per job, so nothing sits below the fold. A dispatcher sees only their own district (the server
 * decides); a system admin picks one, and the hub tabs appear once they have.
 */
export function DispatchBoardPage() {
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  const [params, setParams] = useSearchParams();
  // The control tower links here with ?district= for an admin who picked one there.
  const [adminDistrict, setAdminDistrict] = useState<string>(() => {
    const asked = params.get('district');
    return DISTRICT_OPTIONS.some(({ value }) => value === asked) ? asked! : ALL_DISTRICTS;
  });
  const scope =
    isAdmin && adminDistrict !== ALL_DISTRICTS ? (adminDistrict as SriLankanDistrict) : undefined;
  const hubTabs = !isAdmin || scope !== undefined;

  // Other pages link here with ?q=<tracking number>.
  const [query, setQuery] = useState(() => params.get('q') ?? '');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<DispatchAssignmentResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [hubBusy, setHubBusy] = useState(false);
  // Hub steps done here, hidden at once while dispatch catches up. An order takes each step only once
  // per delivery attempt.
  const [done, setDone] = useState<ReadonlySet<string>>(new Set());
  const lagTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(lagTimer.current), []);

  const { unassigned, assigned, ready, incoming, returns, couriers, refresh } = useDispatchData(
    user?.id,
    scope,
    hubTabs
  );

  const suggestions = useQuery({
    queryKey: ['dispatch-suggestions', selectedOrder?.orderId],
    queryFn: () => dispatchApi.getCourierSuggestions(selectedOrder!.orderId!),
    enabled: Boolean(selectedOrder?.orderId),
    staleTime: 0,
    gcTime: 0,
    retry: false
  });

  const pending = (orders: DispatchAssignmentResponse[] | undefined, step: HubStep) =>
    (orders ?? []).filter((order) => !done.has(stepKey(order, step)));
  // A parcel staying in the district leaves this list by being sorted for delivery instead.
  const readyOrders = pending(ready.data, 'IN_TRANSIT').filter(
    (order) => !done.has(stepKey(order, 'AT_DESTINATION_HUB'))
  );
  const incomingOrders = pending(incoming.data, 'AT_DESTINATION_HUB');
  // What is shown of a return follows from its stage, so any step taken from that stage hides it.
  const returnOrders = (returns.data ?? []).filter(
    (order) => !done.has(stepKey(order, order.hubStage ?? ''))
  );

  const tabs = [
    { id: 'assign', label: 'To assign', count: unassigned.data?.length, list: unassigned },
    { id: 'assigned', label: 'Assigned', count: assigned.data?.total, list: assigned },
    ...(hubTabs
      ? [
          {
            id: 'ready',
            label: 'Ready to send',
            count: ready.data && readyOrders.length,
            list: ready
          },
          {
            id: 'incoming',
            label: 'Incoming',
            count: incoming.data && incomingOrders.length,
            list: incoming
          },
          {
            id: 'returns',
            label: 'Returns',
            count: returns.data && returnOrders.length,
            list: returns
          }
        ]
      : []),
    { id: 'couriers', label: 'Couriers', count: couriers.data?.length, list: couriers }
  ];
  const current = tabs.find((tab) => tab.id === params.get('tab')) ?? tabs[0];
  const openTab = (id: string) => setParams({ tab: id }, { replace: true });

  const searching = query.trim() !== '';
  const search = (orders: DispatchAssignmentResponse[]) =>
    orders.filter((order) => matchesOrder(order, query));
  const lists = [unassigned, assigned, ready, incoming, returns, couriers];
  const failed = lists.some((list) => list.isError);
  const fetching = lists.some((list) => list.isFetching);
  // Waiting times are measured at the last refresh, which is at most a few seconds ago.
  const now = Math.max(...lists.map((list) => list.dataUpdatedAt));

  const openAssignment = (order: DispatchAssignmentResponse | null) => {
    setAssignError(null);
    setSelectedOrder(order);
  };

  const handleAssign = async (courierId: string) => {
    if (!selectedOrder?.orderId) return;
    // An order that already has a courier is moved with /reassign, naming the courier the dispatcher
    // saw so the backend can reject the change if the assignment moved meanwhile.
    const currentCourierId = selectedOrder.courierId;
    const request = {
      orderId: selectedOrder.orderId,
      assignedBy: 'DISPATCHER',
      reason: `Dispatcher console ${currentCourierId ? 'reassignment' : 'assignment'}`
    };
    setSubmitting(true);
    setNotice(null);
    setAssignError(null);
    try {
      if (currentCourierId) {
        await dispatchApi.reassign({
          ...request,
          expectedCurrentCourierId: currentCourierId,
          newCourierId: courierId
        });
      } else {
        await dispatchApi.manualAssign({ ...request, courierId });
      }
      setNotice({
        tone: 'success',
        title: `${orderLabel(selectedOrder)} ${currentCourierId ? 'reassigned' : 'assigned'}`
      });
      setSelectedOrder(null);
    } catch (error) {
      // Stay open with fresh loads, so the dispatcher can pick someone else.
      setAssignError(errorText(error, 'Close this, refresh and try again.'));
      void suggestions.refetch();
    } finally {
      setSubmitting(false);
      void refresh();
    }
  };

  /** Marks parcels Outbound from this hub, accepts ones arriving at it, or decides on a failed delivery. */
  const runHubStep = async (
    orders: DispatchAssignmentResponse[],
    target: HubStep,
    reason?: DeliveryFailureReason
  ) => {
    if (hubBusy || orders.length === 0) return;
    setHubBusy(true);
    setNotice(null);
    const failures: string[] = [];
    let completed = 0;
    for (const order of orders) {
      const failure = await hubStep(order, target, reason);
      if (failure) failures.push(failure);
      else {
        completed += 1;
        setDone((keys) =>
          new Set(keys).add(stepKey(order, target)).add(stepKey(order, order.hubStage ?? ''))
        );
      }
    }
    const retry = orders.some((order) => order.hubStage === 'HELD_AT_HUB');
    setNotice(hubNotice(target, completed, failures, retry && target === 'AT_DESTINATION_HUB'));
    setHubBusy(false);
    void refresh();
    window.clearTimeout(lagTimer.current);
    lagTimer.current = window.setTimeout(() => void refresh(), EVENT_LAG_MS);
  };

  const renderTab = () => {
    // A list that never loaded is not an empty one: the banner above says why.
    if (current.list.data === undefined) {
      return current.list.isError ? null : <TableSkeleton />;
    }
    switch (current.id) {
      case 'assigned': {
        const orders = assigned.data?.orders ?? [];
        return (
          <AssignedTab
            orders={search(orders)}
            hiddenCount={Math.max(0, (assigned.data?.total ?? 0) - orders.length)}
            searching={searching}
            now={now}
            onReassign={openAssignment}
          />
        );
      }
      case 'ready':
        return (
          <ReadyToSendTab
            orders={search(readyOrders)}
            searching={searching}
            busy={hubBusy}
            now={now}
            onRun={(orders) => void runHubStep(orders, 'IN_TRANSIT')}
            onSort={(orders) => void runHubStep(orders, 'AT_DESTINATION_HUB')}
          />
        );
      case 'incoming':
        return (
          <IncomingTab
            orders={search(incomingOrders)}
            searching={searching}
            busy={hubBusy}
            now={now}
            onRun={(orders) => void runHubStep(orders, 'AT_DESTINATION_HUB')}
          />
        );
      case 'returns':
        return (
          <ReturnsTab
            orders={search(returnOrders)}
            searching={searching}
            busy={hubBusy}
            now={now}
            onRun={(orders, step, reason) => void runHubStep(orders, step, reason)}
          />
        );
      case 'couriers':
        return (
          <>
            {isAdmin && !scope ? (
              <div className="px-3 pb-3">
                <LiveFleetHeatmap couriers={couriers.data} />
              </div>
            ) : null}
            <CouriersTab
              couriers={(couriers.data ?? []).filter((courier) => matchesCourier(courier, query))}
              searching={searching}
              showDistrict={isAdmin && !scope}
            />
          </>
        );
      default:
        return (
          <ToAssignTab
            orders={search(unassigned.data ?? [])}
            searching={searching}
            now={now}
            showDistrict={isAdmin && !scope}
            onAssign={openAssignment}
            onShopAssigned={(title) => {
              setNotice({ tone: 'success', title });
              void refresh();
            }}
          />
        );
    }
  };

  return (
    <main className="mx-auto max-w-[1600px] space-y-4 p-4 md:p-6" data-density="compact">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-h1 text-primary">Dispatch</h1>
          {!isAdmin && user?.district ? (
            <p className="text-caption text-secondary">{districtLabel(user.district)} district</p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-end gap-2">
          {isAdmin ? (
            <SelectDropdown
              label="District"
              searchable
              options={DISTRICT_OPTIONS}
              value={adminDistrict}
              onChange={setAdminDistrict}
              className="w-56"
            />
          ) : null}
          <Button
            variant="outline"
            density="compact"
            leftIcon={<RefreshCw className={fetching ? 'animate-spin' : undefined} />}
            onClick={() => void refresh()}
          >
            Refresh
          </Button>
        </div>
      </header>

      {failed ? (
        <StatusBanner tone="warning" title="Some lists could not be refreshed">
          Retrying automatically. Anything shown may be out of date.
        </StatusBanner>
      ) : null}
      {notice ? (
        <StatusBanner tone={notice.tone} title={notice.title} onDismiss={() => setNotice(null)}>
          {notice.body}
        </StatusBanner>
      ) : null}

      <SurfaceCard density="compact" className="overflow-hidden p-0">
        <Tabs
          aria-label="Dispatch queues"
          tabs={tabs.map(({ id, label, count }) => ({ id, label, count }))}
          value={current.id}
          onChange={openTab}
          className="px-1"
        />
        <div role="tabpanel" aria-label={current.label}>
          <div className="p-3">
            <Input
              variant="search"
              density="compact"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onClear={() => setQuery('')}
              placeholder={
                current.id === 'couriers' ? 'Search couriers' : 'Search tracking number or district'
              }
              aria-label={`Search ${current.label}`}
              className="max-w-80"
            />
          </div>
          {renderTab()}
        </div>
      </SurfaceCard>

      <ManualDispatchOverrideModal
        order={selectedOrder}
        onClose={() => openAssignment(null)}
        suggestions={suggestions.data}
        loadError={suggestions.isError}
        submitting={submitting}
        error={assignError}
        onSubmit={(courierId) => void handleAssign(courierId)}
      />
    </main>
  );
}

export default DispatchBoardPage;
