import { ChevronDown, Search } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface FilterOption {
  id: string;
  /** Name of the filter, e.g. `District`. */
  label: string;
  /** Current value, e.g. `Colombo` or `All`. */
  value: string;
  /** Highlights the chip when the filter narrows the list. */
  active?: boolean;
}

export interface FilterBarProps {
  searchValue: string;
  onSearchChange: (value: string) => void;
  searchPlaceholder?: string;
  filters: FilterOption[];
  /** Opens the filter's picker (the caller owns the menu). */
  onFilterClick: (id: string) => void;
  onClearAll?: () => void;
  className?: string;
}

/** Figma FilterBar: search field, labelled filter chips and a "Clear all" action. */
export function FilterBar({
  searchValue,
  onSearchChange,
  searchPlaceholder = 'Search tracking no. or receiver',
  filters,
  onFilterClick,
  onClearAll,
  className
}: FilterBarProps) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-2 border-b border-border-subtle bg-card px-4 py-3',
        className
      )}
    >
      <label className="flex w-[300px] max-w-full items-center gap-2 rounded-lg border border-border-subtle bg-card px-3 py-2 focus-within:outline focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-border-focus">
        <Search aria-hidden="true" className="size-4 shrink-0 text-muted" />
        <input
          type="search"
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={searchPlaceholder}
          aria-label={searchPlaceholder}
          className="min-w-0 flex-1 bg-transparent text-body text-primary outline-none placeholder:text-muted"
        />
      </label>
      {filters.map((filter) => (
        <button
          key={filter.id}
          type="button"
          onClick={() => onFilterClick(filter.id)}
          aria-label={`${filter.label}: ${filter.value}`}
          className={cn(
            'inline-flex items-center gap-1 rounded-lg border px-2.5 py-[7px] text-label transition-colors',
            filter.active
              ? 'border-border-selected bg-brand-subtle'
              : 'border-border-subtle bg-card hover:bg-elevated',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus'
          )}
        >
          <span className="text-secondary">{filter.label}:</span>
          <span className={filter.active ? 'text-brand-pressed' : 'text-primary'}>
            {filter.value}
          </span>
          <ChevronDown aria-hidden="true" className="size-3.5 text-secondary" />
        </button>
      ))}
      {onClearAll ? (
        <button
          type="button"
          onClick={onClearAll}
          className="ml-auto text-label text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          Clear all
        </button>
      ) : null}
    </div>
  );
}

export default FilterBar;
