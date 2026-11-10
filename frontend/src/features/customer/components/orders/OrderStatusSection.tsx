import { forwardRef } from 'react';
import {
  Alert,
  STATUS_PROGRESS_STEPS,
  StatusProgress,
  StatusTimeline,
  SurfaceCard,
  useMediaQuery,
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
 * Where the parcel is and what happens next, plus the real history once it has loaded. The
 * horizontal progress bar needs room for five labels, so narrow screens get the same steps as a
 * vertical timeline.
 */
export const OrderStatusSection = forwardRef<HTMLDivElement, OrderStatusSectionProps>(
  function OrderStatusSection({ order, history }, ref) {
    const wide = useMediaQuery('(min-width: 640px)');
    const step = progressStep(orderStatus(order));
    const next = nextStep(order);

    return (
      <div ref={ref} tabIndex={-1} className="outline-none">
        <SurfaceCard title="Status">
          {step ? (
            <div className="flex flex-col gap-4">
              {next ? <p className="text-body-strong text-primary">{next}</p> : null}
              {wide ? (
                <StatusProgress current={step} />
              ) : (
                <StatusTimeline bare steps={timelineSteps(step, order)} />
              )}
              <p className="text-caption text-secondary">
                Updated {formatDate(order.updatedAt, true)}
              </p>
            </div>
          ) : (
            <TerminalStatus order={order} />
          )}
          <StatusHistory history={history} />
        </SurfaceCard>
      </div>
    );
  }
);

function timelineSteps(
  current: (typeof STATUS_PROGRESS_STEPS)[number]['status'],
  order: CustomerOrderResponse
): StatusTimelineEntry[] {
  const currentIndex = STATUS_PROGRESS_STEPS.findIndex((item) => item.status === current);
  const delivered = current === 'DELIVERED';
  return STATUS_PROGRESS_STEPS.map((item, index) => ({
    key: item.status,
    title: item.label,
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

/** Every status the parcel has reached, with the time. Hidden when there is nothing to show. */
function StatusHistory({ history }: { history?: OrderStatusHistoryEntry[] }) {
  const entries = (history ?? []).filter((entry) => entry.status && entry.occurredAt);
  if (entries.length === 0) return null;

  const steps: StatusTimelineEntry[] = entries.map((entry, index) => ({
    key: `${entry.status}-${entry.occurredAt}-${index}`,
    title: STATUS_LABEL[entry.status as keyof typeof STATUS_LABEL] ?? String(entry.status),
    state: index === entries.length - 1 ? 'current' : 'done',
    meta: formatDate(entry.occurredAt, true)
  }));

  return (
    <div className="mt-5 flex flex-col gap-2 border-t border-border-subtle pt-4">
      <h3 className="text-label font-semibold text-primary">History</h3>
      <StatusTimeline bare steps={steps} />
    </div>
  );
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
      <Alert tone="warning" title="Returning to you">
        This parcel could not be delivered and is on its way back to the pickup address.
      </Alert>
    );
  }
  return (
    <Alert tone="danger" title="Delivery failed">
      This parcel could not be delivered.
    </Alert>
  );
}
