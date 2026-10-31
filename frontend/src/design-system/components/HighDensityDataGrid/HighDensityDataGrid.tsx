import { useState, type ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { Input } from '@/design-system/primitives/Input';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export interface DataGridColumn<T> {
  key: keyof T;
  label: string;
  render?: (row: T) => ReactNode;
  align?: 'left' | 'right';
}

export interface HighDensityDataGridProps<T extends { id: string | number }> {
  rows: T[];
  columns: DataGridColumn<T>[];
  label?: string;
  filterPlaceholder?: string;
  emptyMessage?: string;
  density?: DensityMode;
  className?: string;
}

export function HighDensityDataGrid<T extends { id: string | number }>({
  rows,
  columns,
  label = 'Records',
  filterPlaceholder = 'Filter records',
  emptyMessage = 'No records match this filter.',
  density,
  className
}: HighDensityDataGridProps<T>) {
  const contextDensity = useDensityMode();
  const resolvedDensity = density ?? contextDensity;
  const recipe = densityRecipes[resolvedDensity];
  const [query, setQuery] = useState('');
  const normalized = query.trim().toLowerCase();

  const filtered = normalized
    ? rows.filter((row) =>
        columns.some((column) =>
          String(row[column.key] ?? '')
            .toLowerCase()
            .includes(normalized)
        )
      )
    : rows;

  return (
    <div className={cn('flex flex-col', recipe.stackGap, className)}>
      <div className="flex items-center gap-2">
        <Input
          variant="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onClear={() => setQuery('')}
          placeholder={filterPlaceholder}
          aria-label={filterPlaceholder}
          density={resolvedDensity}
          className="max-w-xs"
        />
        <p className={cn('ml-auto shrink-0 text-muted tabular-nums', recipe.caption)}>
          {filtered.length} / {rows.length}
        </p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-border-subtle bg-card">
        <table
          className={cn('w-full border-collapse text-left', recipe.caption)}
          aria-label={label}
        >
          <thead className="bg-elevated text-secondary">
            <tr>
              {columns.map((column) => (
                <th
                  key={String(column.key)}
                  scope="col"
                  className={cn(
                    'whitespace-nowrap text-overline uppercase',
                    recipe.gridCell,
                    column.align === 'right' ? 'text-right' : 'text-left'
                  )}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="text-primary tabular-nums">
            {filtered.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className={cn('text-center text-muted', recipe.gridCell)}
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              filtered.map((row) => (
                <tr
                  key={String(row.id)}
                  className="border-t border-border-subtle transition-colors hover:bg-canvas"
                >
                  {columns.map((column) => (
                    <td
                      key={String(column.key)}
                      className={cn(
                        'whitespace-nowrap',
                        recipe.gridCell,
                        column.align === 'right' ? 'text-right' : 'text-left'
                      )}
                    >
                      {column.render ? column.render(row) : String(row[column.key] ?? '')}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default HighDensityDataGrid;
