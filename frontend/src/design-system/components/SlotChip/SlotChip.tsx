import { CalendarClock } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/utils/cn';
import { ChoiceChip } from '@/design-system/components/ChoiceChip/ChoiceChip';

export interface SlotChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  /** e.g. `14:00–16:00`. */
  time: string;
  booked: number;
  capacity: number;
  selected?: boolean;
}

/** Figma SlotChip: a pickup window with its load. Full slots are disabled and read "Full". */
export const SlotChip = forwardRef<HTMLButtonElement, SlotChipProps>(
  ({ time, booked, capacity, selected = false, className, ...props }, ref) => {
    const full = booked >= capacity;
    return (
      <button
        {...props}
        ref={ref}
        type="button"
        disabled={full || props.disabled}
        aria-pressed={selected}
        className={cn(
          'flex min-w-28 flex-col items-start gap-0.5 rounded-lg border px-3 py-2 text-left transition-colors',
          full
            ? 'cursor-not-allowed border-border-subtle bg-disabled-bg'
            : selected
              ? 'border-border-selected bg-brand-subtle shadow-[inset_0_0_0_0.5px_var(--color-border-selected)]'
              : 'border-border-subtle bg-card hover:bg-elevated',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
          className
        )}
      >
        <span
          className={cn(
            'text-label tabular-nums',
            full ? 'text-disabled-fg' : selected ? 'text-brand-pressed' : 'text-primary'
          )}
        >
          {time}
        </span>
        <span className={cn('text-caption', full ? 'text-disabled-fg' : 'text-secondary')}>
          {full ? 'Full' : `${booked} of ${capacity} booked`}
        </span>
      </button>
    );
  }
);
SlotChip.displayName = 'SlotChip';

export interface PickupSlot {
  id: string;
  time: string;
  booked: number;
  capacity: number;
}

export interface PickupSlotPickerProps {
  days: { id: string; label: string }[];
  selectedDay: string;
  onDayChange: (id: string) => void;
  slots: PickupSlot[];
  selectedSlot?: string;
  onSlotChange: (id: string) => void;
  className?: string;
}

/** Figma PickupSlotPicker: day chips above a grid of `SlotChip`s. */
export function PickupSlotPicker({
  days,
  selectedDay,
  onDayChange,
  slots,
  selectedSlot,
  onSlotChange,
  className
}: PickupSlotPickerProps) {
  return (
    <section
      aria-label="Pickup time"
      className={cn(
        'flex w-full max-w-[454px] flex-col gap-3 rounded-xl border border-border-subtle bg-card p-4',
        className
      )}
    >
      <h3 className="flex items-center gap-2 text-body-strong text-primary">
        <CalendarClock aria-hidden="true" className="size-[18px] text-secondary" />
        Pickup time
      </h3>
      <div role="group" aria-label="Pickup day" className="flex flex-wrap gap-2">
        {days.map((day) => (
          <ChoiceChip
            key={day.id}
            selected={day.id === selectedDay}
            onClick={() => onDayChange(day.id)}
          >
            {day.label}
          </ChoiceChip>
        ))}
      </div>
      <div role="group" aria-label="Pickup window" className="flex flex-wrap gap-2">
        {slots.map((slot) => (
          <SlotChip
            key={slot.id}
            time={slot.time}
            booked={slot.booked}
            capacity={slot.capacity}
            selected={slot.id === selectedSlot}
            onClick={() => onSlotChange(slot.id)}
          />
        ))}
      </div>
    </section>
  );
}

export default SlotChip;
