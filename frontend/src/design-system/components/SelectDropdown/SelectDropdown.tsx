import { KeyboardEvent, useEffect, useId, useMemo, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/utils/cn';
import { DropdownMenu } from '@/design-system/components/DropdownMenu/DropdownMenu';
import {
  DropdownItem,
  type DropdownItemStatus
} from '@/design-system/components/DropdownMenu/DropdownItem';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
  status?: DropdownItemStatus;
  description?: string;
}

export type SelectDropdownState = 'default' | 'focused' | 'open' | 'error' | 'disabled';

export interface SelectDropdownProps {
  options: SelectOption[];
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  label?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  /** Forces a single visual state. Omit for live behaviour. */
  state?: SelectDropdownState;
  searchable?: boolean;
  density?: DensityMode;
  id?: string;
  name?: string;
  className?: string;
}

const focusRing = 'outline outline-2 outline-offset-[-2px] outline-border-focus';
const focusRingError = 'outline outline-2 outline-offset-[-2px] outline-status-danger';
const liveFocus =
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-border-focus';
const liveFocusError =
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-status-danger';

export function SelectDropdown({
  options,
  value,
  onChange,
  placeholder = 'Select an option',
  label,
  hint,
  error,
  required = false,
  disabled = false,
  state,
  searchable = false,
  density,
  id,
  name,
  className
}: SelectDropdownProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];
  const generatedId = useId();
  const triggerId = id ?? `select-${generatedId}`;
  const listId = `${triggerId}-listbox`;

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);

  const triggerRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const isInvalid = state === 'error' || Boolean(error);
  const isDisabled = state === 'disabled' || disabled;
  const isMenuOpen = state === 'open' || open;

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return options;
    return options.filter((option) => option.label.toLowerCase().includes(needle));
  }, [options, query]);

  const selectedOption = options.find((option) => option.value === value);
  const describedBy = error ? `${triggerId}-error` : hint ? `${triggerId}-hint` : undefined;
  const activeOptionId =
    isMenuOpen && filtered[activeIndex] ? `${listId}-option-${activeIndex}` : undefined;

  const openMenu = () => {
    setOpen(true);
    setQuery('');
    const index = options.findIndex((option) => option.value === value);
    setActiveIndex(index >= 0 ? index : 0);
  };

  const closeMenu = (restoreFocus = true) => {
    setOpen(false);
    setQuery('');
    if (restoreFocus) triggerRef.current?.focus();
  };

  useEffect(() => {
    if (isMenuOpen && searchable) searchRef.current?.focus();
  }, [isMenuOpen, searchable]);

  useEffect(() => {
    setActiveIndex((current) =>
      current < filtered.length ? current : Math.max(filtered.length - 1, 0)
    );
  }, [filtered.length]);

  const moveActive = (delta: number) => {
    if (filtered.length === 0) return;
    let next = activeIndex;
    for (let step = 0; step < filtered.length; step += 1) {
      next = (next + delta + filtered.length) % filtered.length;
      if (!filtered[next].disabled) break;
    }
    setActiveIndex(next);
  };

  const select = (option: SelectOption | undefined) => {
    if (!option || option.disabled) return;
    onChange?.(option.value);
    closeMenu();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (isDisabled) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!isMenuOpen) openMenu();
        else moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (!isMenuOpen) {
          openMenu();
          setActiveIndex(Math.max(filtered.length - 1, 0));
        } else moveActive(-1);
        break;
      case 'Home':
        if (isMenuOpen) {
          event.preventDefault();
          setActiveIndex(0);
        }
        break;
      case 'End':
        if (isMenuOpen) {
          event.preventDefault();
          setActiveIndex(Math.max(filtered.length - 1, 0));
        }
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        if (!isMenuOpen) openMenu();
        else select(filtered[activeIndex]);
        break;
      case 'Escape':
        if (isMenuOpen) {
          event.preventDefault();
          closeMenu();
        }
        break;
      case 'Tab':
        if (isMenuOpen) closeMenu(false);
        break;
      default:
        break;
    }
  };

  const surfaces: Record<string, string> = {
    default: 'bg-card border-border-subtle text-primary',
    focused: 'bg-card border-border-subtle text-primary',
    open: 'bg-card border-border-subtle text-primary',
    error: 'bg-card border-status-danger text-primary',
    disabled: 'bg-disabled-bg border-border-subtle text-disabled-fg'
  };

  const resolvedState =
    state ?? (isDisabled ? 'disabled' : isInvalid ? 'error' : isMenuOpen ? 'open' : 'default');
  const showStaticRing = resolvedState === 'focused' || resolvedState === 'open';
  const showLiveRing = resolvedState === 'default' || resolvedState === 'error';

  return (
    <div className={cn('relative w-full', className)} onKeyDown={onKeyDown}>
      {label ? (
        <label
          htmlFor={triggerId}
          className={cn('mb-1 block font-semibold text-primary', recipe.caption)}
        >
          {label} {required && <span className="text-status-danger">*</span>}
        </label>
      ) : null}
      <button
        ref={triggerRef}
        id={triggerId}
        name={name}
        type="button"
        role="combobox"
        aria-expanded={isMenuOpen}
        aria-controls={listId}
        aria-haspopup="listbox"
        aria-activedescendant={searchable ? undefined : activeOptionId}
        aria-invalid={isInvalid || undefined}
        aria-describedby={describedBy}
        aria-required={required || undefined}
        disabled={isDisabled}
        onClick={() => (isMenuOpen ? closeMenu(false) : openMenu())}
        className={cn(
          'flex w-full items-center gap-2 border text-left transition-colors disabled:cursor-not-allowed',
          recipe.fieldControl,
          recipe.radius,
          surfaces[resolvedState],
          showStaticRing ? (isInvalid ? focusRingError : focusRing) : '',
          showLiveRing ? (isInvalid ? liveFocusError : liveFocus) : ''
        )}
      >
        <span
          className={cn('min-w-0 flex-1 truncate', selectedOption ? 'text-primary' : 'text-muted')}
        >
          {selectedOption?.label ?? placeholder}
        </span>
        <ChevronDown
          className={cn(
            'shrink-0 text-muted transition-transform',
            recipe.icon,
            isMenuOpen && 'rotate-180'
          )}
          aria-hidden="true"
        />
      </button>

      <DropdownMenu
        open={isMenuOpen}
        onClose={() => closeMenu(true)}
        id={listId}
        label={label ?? placeholder}
        density={density}
        searchable={searchable}
        searchValue={query}
        onSearchChange={setQuery}
        searchInputRef={searchRef}
        searchActiveDescendant={searchable ? activeOptionId : undefined}
        searchListboxId={searchable ? listId : undefined}
        footer={`${filtered.length} option${filtered.length === 1 ? '' : 's'}`}
      >
        {filtered.map((option, index) => (
          <DropdownItem
            key={option.value}
            id={`${listId}-option-${index}`}
            density={density}
            selected={option.value === value}
            active={index === activeIndex}
            disabled={option.disabled}
            status={option.status}
            description={option.description}
            onSelect={() => select(option)}
          >
            {option.label}
          </DropdownItem>
        ))}
      </DropdownMenu>

      {error ? (
        <span
          id={`${triggerId}-error`}
          className={cn('mt-1 block text-status-danger', recipe.caption)}
        >
          {error}
        </span>
      ) : hint ? (
        <span id={`${triggerId}-hint`} className={cn('mt-1 block text-secondary', recipe.caption)}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

export default SelectDropdown;
