import { MoreHorizontal } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import type { PublicOrderStatus } from '@/types/contracts';
import { Checkbox } from '@/design-system/components/Checkbox/Checkbox';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { SpeedTag, type Speed } from '@/design-system/components/SpeedTag/SpeedTag';
import { TrackingNumber } from '@/design-system/components/TrackingNumber/TrackingNumber';
import { TableHeaderCell, type SortDirection } from './TableHeaderCell';

/** Figma column widths: select 40, tracking 170, status 170, route 220, speed 110, receiver 180, fee 120, actions 56. */
const rowGrid =
  'grid min-w-[1066px] grid-cols-[40px_170px_170px_220px_110px_180px_minmax(120px,1fr)_56px] items-center gap-x-1.5 px-4';

export interface OrderTableColumn {
  key: 'tracking' | 'status' | 'route' | 'speed' | 'receiver' | 'fee';
  label: string;
  sort?: SortDirection;
  onSort?: () => void;
}

export interface OrderTableProps {
  columns: OrderTableColumn[];
  /** Header checkbox state for "select all". */
  allSelected?: boolean | 'mixed';
  onToggleAll?: (selected: boolean) => void;
  /** Rows: pass `OrderTableRow` elements. */
  children: ReactNode;
  label?: string;
  className?: string;
}

/** Figma TableHeaderRow + body wrapper for `OrderTableRow`s. Scrolls horizontally on narrow screens. */
export function OrderTable({
  columns,
  allSelected = false,
  onToggleAll,
  children,
  label = 'Orders',
  className
}: OrderTableProps) {
  return (
    <div className={cn('overflow-x-auto', className)}>
      <div role="table" aria-label={label}>
        <div role="rowgroup">
          <div
            role="row"
            className={cn(rowGrid, 'border-b border-border-subtle bg-elevated py-2.5')}
          >
            <div role="columnheader">
              <Checkbox
                aria-label="Select all orders"
                checked={allSelected === true}
                mixed={allSelected === 'mixed'}
                onChange={(event) => onToggleAll?.(event.target.checked)}
              />
            </div>
            {columns.map((column) => (
              <TableHeaderCell
                key={column.key}
                label={column.label}
                sort={column.sort}
                onSort={column.onSort}
              />
            ))}
            <div role="columnheader" aria-label="Actions" />
          </div>
        </div>
        <div role="rowgroup">{children}</div>
      </div>
    </div>
  );
}

export interface OrderTableRowData {
  id: string;
  trackingNumber: string;
  status: PublicOrderStatus;
  from: string;
  to: string;
  speed: Speed;
  receiverName: string;
  /** e.g. `00300 · Colombo 03`. */
  receiverArea: string;
  fee: number;
}

export interface OrderTableRowProps {
  order: OrderTableRowData;
  selected?: boolean;
  onSelectedChange?: (selected: boolean) => void;
  /** Row click, e.g. open the order. */
  onOpen?: () => void;
  onMore?: () => void;
  className?: string;
}

/** Figma OrderTableRow: State = Default / Hover (canvas tint) / Selected (brand tint). */
export function OrderTableRow({
  order,
  selected = false,
  onSelectedChange,
  onOpen,
  onMore,
  className
}: OrderTableRowProps) {
  return (
    <div
      role="row"
      aria-selected={selected}
      data-selected={selected || undefined}
      onClick={onOpen}
      className={cn(
        rowGrid,
        'border-b border-border-subtle py-2.5 transition-colors',
        selected ? 'bg-brand-subtle' : 'bg-card hover:bg-canvas',
        onOpen && 'cursor-pointer',
        className
      )}
    >
      <div role="cell" onClick={(event) => event.stopPropagation()}>
        <Checkbox
          aria-label={`Select ${order.trackingNumber}`}
          checked={selected}
          onChange={(event) => onSelectedChange?.(event.target.checked)}
        />
      </div>
      <div role="cell">
        <TrackingNumber value={order.trackingNumber} copyable={false} />
      </div>
      <div role="cell">
        <FsmStatusBadge state={order.status} size="md" />
      </div>
      <div role="cell" className="flex items-center gap-1.5 text-body text-primary">
        <span className="truncate">{order.from}</span>
        <span aria-label="to" className="text-muted">
          →
        </span>
        <span className="truncate">{order.to}</span>
      </div>
      <div role="cell">
        <SpeedTag speed={order.speed} />
      </div>
      <div role="cell" className="flex min-w-0 flex-col">
        <span className="truncate text-body text-primary">{order.receiverName}</span>
        <span className="truncate text-caption text-secondary">{order.receiverArea}</span>
      </div>
      <div role="cell">
        <MoneyAmount amount={order.fee} />
      </div>
      <div role="cell" onClick={(event) => event.stopPropagation()}>
        <button
          type="button"
          onClick={onMore}
          aria-label={`More actions for ${order.trackingNumber}`}
          className="inline-flex size-8 items-center justify-center rounded-lg text-secondary hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
        >
          <MoreHorizontal aria-hidden="true" className="size-[18px]" />
        </button>
      </div>
    </div>
  );
}

export default OrderTableRow;
