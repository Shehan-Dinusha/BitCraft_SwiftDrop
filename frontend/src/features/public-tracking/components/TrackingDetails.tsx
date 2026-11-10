import {
  Alert,
  FsmStatusBadge,
  LiveIndicator,
  StatusProgress,
  StatusTimeline,
  SurfaceCard,
  TrackingNumber,
  useMediaQuery,
  type AlertTone,
  type LiveIndicatorState,
  type StatusProgressStatus,
  type StatusTimelineEntry,
  type TimelineStepState
} from '@/design-system';
import { cn } from '@/utils/cn';
import {
  isTerminalPublicStatus,
  type PublicOrderStatus,
  type PublicTrackingView,
  type SriLankanDistrict
} from '@/services/api/trackingHubApi';

const STATUS_LABELS: Record<PublicOrderStatus, string> = {
  CREATED: 'Created',
  PICKUP_ASSIGNED: 'Pickup assigned',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  FAILED: 'Delivery failed',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RETURNED_TO_SENDER: 'Returned to sender'
};

const PROGRESS_STATUSES: StatusProgressStatus[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED'
];

const TERMINAL_ALERTS: Partial<
  Record<PublicOrderStatus, { tone: AlertTone; title: string; message: string }>
> = {
  FAILED: {
    tone: 'danger',
    title: 'Delivery failed',
    message: 'This parcel could not be delivered. The sender will be contacted with the next steps.'
  },
  CANCELLED: {
    tone: 'warning',
    title: 'Parcel cancelled',
    message: 'This delivery was cancelled. Contact the sender if you need more information.'
  },
  RETURNED_TO_SENDER: {
    tone: 'warning',
    title: 'Returning to sender',
    message: 'This parcel is on its way back to the sender.'
  }
};

const TIMESTAMP_FORMATTER = new Intl.DateTimeFormat('en-LK', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
});

export interface TrackingDetailsProps {
  view: PublicTrackingView;
  connection: LiveIndicatorState;
  className?: string;
}

export function TrackingDetails({ view, connection, className }: TrackingDetailsProps) {
  const isWide = useMediaQuery('(min-width: 640px)');
  const isTerminal = isTerminalPublicStatus(view.currentPublicStatus);
  const progressStatus = toProgressStatus(view.currentPublicStatus);
  const terminalAlert = TERMINAL_ALERTS[view.currentPublicStatus];
  const timeline = buildTimeline(view);

  return (
    <div className={cn('flex flex-col gap-5', className)}>
      <SurfaceCard>
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex min-w-0 flex-col gap-1.5">
              <span className="text-overline text-secondary">Tracking number</span>
              <TrackingNumber
                value={view.trackingNumber}
                size={isWide ? 'lg' : 'md'}
                className="max-w-full"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {isTerminal ? null : <LiveIndicator state={connection} />}
              <FsmStatusBadge state={view.currentPublicStatus} size="md" />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 rounded-lg border border-border-subtle bg-canvas p-3 sm:gap-4 sm:p-4">
            <DistrictSummary label="From" district={view.originDistrict} />
            <DistrictSummary label="To" district={view.destinationDistrict} />
          </div>

          {progressStatus ? <StatusProgress current={progressStatus} className="py-1" /> : null}

          {terminalAlert ? (
            <Alert tone={terminalAlert.tone} title={terminalAlert.title}>
              {terminalAlert.message}
            </Alert>
          ) : null}
        </div>
      </SurfaceCard>

      <StatusTimeline steps={timeline} />
    </div>
  );
}

function DistrictSummary({
  label,
  district
}: {
  label: string;
  district: SriLankanDistrict | undefined;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <span className="text-overline text-secondary">{label}</span>
      <span className="truncate text-body-strong text-primary">{formatDistrict(district)}</span>
    </div>
  );
}

function buildTimeline(view: PublicTrackingView): StatusTimelineEntry[] {
  if (view.statusTimeline.length === 0) {
    return [
      {
        key: 'current',
        state: 'current',
        title: STATUS_LABELS[view.currentPublicStatus]
      }
    ];
  }

  return view.statusTimeline.map((entry, index) => {
    const isLast = index === view.statusTimeline.length - 1;
    const isFailure =
      entry.status === 'FAILED' ||
      entry.status === 'CANCELLED' ||
      entry.status === 'RETURNED_TO_SENDER';

    const state: TimelineStepState = isFailure
      ? 'failed'
      : isLast
        ? entry.status === 'DELIVERED'
          ? 'done'
          : 'current'
        : 'done';

    const meta = [
      formatTimestamp(entry.timestamp),
      entry.district ? formatDistrict(entry.district) : null
    ]
      .filter((part): part is string => Boolean(part))
      .join(' · ');

    return {
      key: `${entry.status}-${entry.timestamp}-${index}`,
      state,
      title: STATUS_LABELS[entry.status],
      meta
    };
  });
}

function toProgressStatus(status: PublicOrderStatus): StatusProgressStatus | null {
  if (status === 'PICKUP_ASSIGNED') return 'CREATED';
  return PROGRESS_STATUSES.includes(status as StatusProgressStatus)
    ? (status as StatusProgressStatus)
    : null;
}

function formatDistrict(district: SriLankanDistrict | undefined): string {
  if (!district) return 'Not recorded';
  return district
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return TIMESTAMP_FORMATTER.format(date);
}

export default TrackingDetails;
