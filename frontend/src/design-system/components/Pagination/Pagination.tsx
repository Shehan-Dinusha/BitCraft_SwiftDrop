import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface PaginationProps {
  /** 1-based current page. */
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  /** Noun for the summary, e.g. `orders`. */
  itemLabel?: string;
  className?: string;
}

type PageItem = number | 'gap-start' | 'gap-end';

/** First, last, and the current page with its neighbours; gaps become an ellipsis. */
function pageItems(page: number, pageCount: number): PageItem[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, index) => index + 1);
  const items: PageItem[] = [1];
  const start = Math.max(2, page - 1);
  const end = Math.min(pageCount - 1, page + 1);
  if (start > 2) items.push('gap-start');
  for (let value = start; value <= end; value += 1) items.push(value);
  if (end < pageCount - 1) items.push('gap-end');
  items.push(pageCount);
  return items;
}

const buttonBase =
  'inline-flex size-8 items-center justify-center rounded-lg border text-label transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus disabled:cursor-not-allowed disabled:opacity-50';

/** Figma Pagination: "Showing 1–20 of 248 orders" and numbered page buttons with previous/next. */
export function Pagination({
  page,
  pageSize,
  total,
  onPageChange,
  itemLabel = 'orders',
  className
}: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const first = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const last = Math.min(page * pageSize, total);

  return (
    <nav
      aria-label="Pagination"
      className={cn('flex flex-wrap items-center gap-1 bg-card px-4 py-3', className)}
    >
      <p className="text-caption text-secondary">
        Showing {first}–{last} of {total} {itemLabel}
      </p>
      <div className="ml-auto flex items-center gap-1">
        <button
          type="button"
          aria-label="Previous page"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          className={cn(
            buttonBase,
            'border-border-subtle bg-card text-secondary hover:bg-elevated'
          )}
        >
          <ChevronLeft aria-hidden="true" className="size-4" />
        </button>
        {pageItems(page, pageCount).map((item) =>
          typeof item === 'number' ? (
            <button
              key={item}
              type="button"
              aria-label={`Page ${item}`}
              aria-current={item === page ? 'page' : undefined}
              onClick={() => onPageChange(item)}
              className={cn(
                buttonBase,
                item === page
                  ? 'border-brand-primary bg-brand-primary text-brand-on'
                  : 'border-border-subtle bg-card text-primary hover:bg-elevated'
              )}
            >
              {item}
            </button>
          ) : (
            <span
              key={item}
              aria-hidden="true"
              className="inline-flex size-8 items-center justify-center text-label text-primary"
            >
              …
            </span>
          )
        )}
        <button
          type="button"
          aria-label="Next page"
          disabled={page >= pageCount}
          onClick={() => onPageChange(page + 1)}
          className={cn(
            buttonBase,
            'border-border-subtle bg-card text-secondary hover:bg-elevated'
          )}
        >
          <ChevronRight aria-hidden="true" className="size-4" />
        </button>
      </div>
    </nav>
  );
}

export default Pagination;
