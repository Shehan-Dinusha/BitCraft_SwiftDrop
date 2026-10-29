import { forwardRef, useRef, type ButtonHTMLAttributes, type KeyboardEvent } from 'react';
import { cn } from '@/utils/cn';

export interface TabItemProps extends Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'type' | 'role'
> {
  label: string;
  /** Small count pill after the label (e.g. timeline events). */
  count?: number;
  selected?: boolean;
}

/** Figma TabItem: State = Default / Hover / Selected. Selected gets a 2px brand underline and bold label. */
export const TabItem = forwardRef<HTMLButtonElement, TabItemProps>(
  ({ label, count, selected = false, className, ...props }, ref) => (
    <button
      {...props}
      ref={ref}
      type="button"
      role="tab"
      aria-selected={selected}
      tabIndex={selected ? 0 : -1}
      className={cn(
        '-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-label transition-colors',
        selected
          ? 'border-brand-primary font-semibold text-brand-primary'
          : 'border-transparent text-secondary hover:border-border-subtle hover:text-primary',
        'focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus',
        className
      )}
    >
      {label}
      {count !== undefined ? (
        <span
          className={cn(
            'rounded-full px-1.5 text-caption tabular-nums',
            selected ? 'bg-brand-subtle text-brand-primary' : 'bg-elevated text-secondary'
          )}
        >
          {count}
        </span>
      ) : null}
    </button>
  )
);
TabItem.displayName = 'TabItem';

export interface TabsProps {
  tabs: { id: string; label: string; count?: number }[];
  value: string;
  onChange: (id: string) => void;
  'aria-label'?: string;
  className?: string;
}

/** Figma Tabs: a tab strip with arrow-key navigation. Render the matching panel yourself with `role="tabpanel"`. */
export function Tabs({ tabs, value, onChange, className, ...props }: TabsProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const index = tabs.findIndex((tab) => tab.id === value);
    const move = (next: number) => {
      const target = (next + tabs.length) % tabs.length;
      onChange(tabs[target].id);
      refs.current[target]?.focus();
    };
    if (event.key === 'ArrowRight') move(index + 1);
    else if (event.key === 'ArrowLeft') move(index - 1);
    else if (event.key === 'Home') move(0);
    else if (event.key === 'End') move(tabs.length - 1);
    else return;
    event.preventDefault();
  };

  return (
    <div
      role="tablist"
      aria-label={props['aria-label']}
      onKeyDown={handleKeyDown}
      className={cn('flex gap-1 overflow-x-auto border-b border-border-subtle', className)}
    >
      {tabs.map((tab, index) => (
        <TabItem
          key={tab.id}
          ref={(node) => {
            refs.current[index] = node;
          }}
          label={tab.label}
          count={tab.count}
          selected={tab.id === value}
          onClick={() => onChange(tab.id)}
        />
      ))}
    </div>
  );
}

export default Tabs;
