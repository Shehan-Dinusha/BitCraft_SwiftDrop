import type { ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import { EmptyState, Skeleton, SpeedTag } from '@/design-system';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { cn } from '@/utils/cn';
import { orderLabel, toSpeed } from './dispatchQueues';

/** Padding shared by every body cell. */
export const CELL = 'px-3 py-2.5 align-middle';

/** The compact table every dispatch tab uses. `null` in `headers` leaves a blank header cell. */
export function DispatchTable({
  label,
  headers,
  children
}: {
  label: string;
  headers: (string | null)[];
  children: ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table
        className="w-full min-w-[560px] text-left text-caption text-primary"
        aria-label={label}
      >
        <thead className="border-y border-border-subtle bg-elevated text-overline uppercase text-secondary">
          <tr>
            {headers.map((header, index) => (
              <th
                key={header ?? `blank-${index}`}
                scope="col"
                className={cn('px-3 py-2', header === 'Action' && 'text-right')}
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border-subtle tabular-nums">{children}</tbody>
      </table>
    </div>
  );
}

export function TrackingCell({ order }: { order: DispatchAssignmentResponse }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="whitespace-nowrap font-mono font-semibold">{orderLabel(order)}</span>
      {order.orderSpeed && order.orderSpeed !== 'STANDARD' ? (
        <SpeedTag speed={toSpeed(order.orderSpeed)} />
      ) : null}
    </div>
  );
}

export function RouteCell({ order }: { order: DispatchAssignmentResponse }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      {districtLabel(order.pickupDistrict) || 'Unknown'}
      <ArrowRight aria-label="to" className="size-3.5 text-muted" />
      {districtLabel(order.deliveryDistrict) || 'Unknown'}
    </span>
  );
}

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning';

const badgeTones: Record<BadgeTone, string> = {
  neutral: 'bg-elevated text-secondary',
  info: 'bg-status-info-bg text-status-info-fg',
  success: 'bg-status-success-bg text-status-success-fg',
  warning: 'bg-status-warning-bg text-status-warning-fg'
};

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-caption font-semibold',
        badgeTones[tone]
      )}
    >
      {children}
    </span>
  );
}

export function TableSkeleton() {
  return (
    <div role="status" aria-label="Loading">
      {[0, 1, 2, 3].map((row) => (
        <Skeleton key={row} preset="table-row" />
      ))}
    </div>
  );
}

/** Nothing to show: either the search hid everything or the queue really is empty. */
export function TabEmpty({ searching, title }: { searching: boolean; title: string }) {
  return (
    <div className="flex justify-center p-6">
      <EmptyState
        type={searching ? 'no-results' : 'empty-queue'}
        title={searching ? 'Nothing matches your search' : title}
        message={
          searching
            ? 'Try another tracking number, district or name.'
            : 'This list refreshes by itself.'
        }
        className="border-none"
      />
    </div>
  );
}
