import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { cn } from '@/utils/cn';

export type SortDirection = 'none' | 'ascending' | 'descending';

export interface TableHeaderCellProps {
  label: string;
  sort?: SortDirection;
  /** Makes the label a button that toggles sorting. */
  onSort?: () => void;
  className?: string;
}

/** Figma TableHeaderCell: uppercase label with a sort affordance. Sort = None / Ascending / Descending. */
export function TableHeaderCell({ label, sort = 'none', onSort, className }: TableHeaderCellProps) {
  const active = sort !== 'none';
  const SortIcon =
    sort === 'ascending' ? ArrowUp : sort === 'descending' ? ArrowDown : ChevronsUpDown;
  const content = (
    <>
      <span className="text-overline uppercase">{label}</span>
      {onSort || active ? (
        <SortIcon
          aria-hidden="true"
          className={cn('size-3', active ? 'text-brand-primary' : 'text-muted')}
        />
      ) : null}
    </>
  );

  return (
    <div
      role="columnheader"
      aria-sort={sort}
      className={cn('flex items-center', active ? 'text-primary' : 'text-secondary', className)}
    >
      {onSort ? (
        <button
          type="button"
          onClick={onSort}
          className="inline-flex items-center gap-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          {content}
        </button>
      ) : (
        <span className="inline-flex items-center gap-1">{content}</span>
      )}
    </div>
  );
}

export default TableHeaderCell;
