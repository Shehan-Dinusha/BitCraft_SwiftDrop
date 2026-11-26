import { forwardRef } from 'react';
import {
  Alert,
  STATUS_PROGRESS_STEPS,
  StatusTimeline,
  SurfaceCard,
  type StatusTimelineEntry
} from '@/design-system';
import type { CustomerOrderResponse, OrderStatusHistoryEntry } from '@/services/api/orderApi';
import { STATUS_LABEL, formatDate, nextStep, orderStatus, progressStep } from './orderDisplay';

interface OrderStatusSectionProps {
  order: CustomerOrderResponse;
  /** The statuses the parcel has been through, oldest first. Left out while loading or if it fails. */
  history?: OrderStatusHistoryEntry[];
}

/**
 * Where the parcel is and what happens next, in one timeline: the steps it has been through, with their
 * times, then the steps still to come. Until the history has loaded, the planned steps stand in for it.
 */
export const OrderStatusSection = forwardRef<HTMLDivElement, OrderStatusSectionProps>(
  function OrderStatusSection({ order, history }, ref) {
    const step = progressStep(orderStatus(order));
    const next = nextStep(order);
    const reached = reachedSteps(history);
    const steps =
      reached.length > 0 ? [...reached, ...upcomingSteps(step, reached)] : step ? timelineSteps(step, order) : [];

    return (
      <div ref={ref} tabIndex={-1} className="outline-none">
        <SurfaceCard title="Status">
          <div className="flex flex-col gap-4">
            {step ? (
              next ? <p className="text-body-strong text-primary">{next}</p> : null
            ) : (
              <TerminalStatus order={order} />
            )}
            {steps.length > 0 ? <StatusTimeline bare steps={steps} /> : null}
          </div>
        </SurfaceCard>
      </div>
    );
  }
);

const FAILURE_STATUSES = new Set(['DELIVERY_ATTEMPTED', 'FAILED', 'CANCELLED']);

/** Every status the parcel has reached, with the time; the latest is the current one. */
function reachedSteps(history?: OrderStatusHistoryEntry[]): StatusTimelineEntry[] {
  const entries = (history ?? []).filter((entry) => entry.status && entry.occurredAt);
  return entries.map((entry, index) => {
    const latest = index === entries.length - 1;
    const status = String(entry.status);
    return {
      key: `${status}-${entry.occurredAt}-${index}`,
      title: STATUS_LABEL[entry.status as keyof typeof STATUS_LABEL] ?? status,
      state: FAILURE_STATUSES.has(status)
        ? 'failed'
        : latest && status !== 'DELIVERED'
          ? 'current'
          : 'done',
      meta: formatDate(entry.occurredAt, true)
    };
  });
}

/** The delivery steps after the current one that have not happened yet, greyed out. */
function upcomingSteps(
  current: (typeof STATUS_PROGRESS_STEPS)[number]['status'] | null,
  reached: StatusTimelineEntry[]
): StatusTimelineEntry[] {
  if (!current) return [];
  const seen = new Set(reached.map((entry) => entry.title));
  const from = STATUS_PROGRESS_STEPS.findIndex((item) => item.status === current);
  return STATUS_PROGRESS_STEPS.slice(from + 1)
    .map((item) => ({ key: `next-${item.status}`, title: STATUS_LABEL[item.status], state: 'upcoming' as const }))
    .filter((entry) => !seen.has(entry.title));
}

function timelineSteps(
  current: (typeof STATUS_PROGRESS_STEPS)[number]['status'],
  order: CustomerOrderResponse
): StatusTimelineEntry[] {
  const currentIndex = STATUS_PROGRESS_STEPS.findIndex((item) => item.status === current);
  const delivered = current === 'DELIVERED';
  return STATUS_PROGRESS_STEPS.map((item, index) => ({
    key: item.status,
    title: STATUS_LABEL[item.status],
    state:
      index < currentIndex || delivered ? 'done' : index === currentIndex ? 'current' : 'upcoming',
    meta:
      index === 0
        ? formatDate(order.createdAt, true)
        : index === currentIndex
          ? formatDate(order.updatedAt, true)
          : undefined
  }));
}

/** Wording matches the public tracking page; the customer here is the sender. */
function TerminalStatus({ order }: { order: CustomerOrderResponse }) {
  const status = orderStatus(order);
  if (status === 'CANCELLED') {
    const declinedFee = order.feeRevisionStatus === 'REJECTED';
    return (
      <Alert
        tone="info"
        title={`Cancelled ${formatDate(order.cancelledAt ?? order.updatedAt, true)}`}
      >
        {declinedFee
          ? 'You declined the updated fee, so this parcel was cancelled at no charge.'
          : order.cancellationReason
            ? `Reason: ${order.cancellationReason}`
            : 'This parcel was cancelled.'}
      </Alert>
    );
  }
  if (status === 'RETURNED_TO_SENDER') {
    return (
      <Alert tone="info" title={`Returned ${formatDate(order.updatedAt, true)}`}>
        This parcel could not be delivered and was handed back to you.
      </Alert>
    );
  }
  if (status === 'RETURNING') {
    return (
      <Alert tone="warning" title="Coming back to you">
        This parcel could not be delivered. A courier brings it back to the pickup address. The
        return is free.
      </Alert>
    );
  }
  return (
    <Alert tone="danger" title="Delivery failed">
      This parcel could not be delivered.
    </Alert>
  );
}
