import { Bell, ChevronDown, ChevronRight, Package, Search, Sparkles, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState, useEffect, useRef } from 'react';
import { cn } from '@/utils/cn';
import { useMediaQuery } from '@/design-system/components/Shell/useMediaQuery';
import { Button } from '@/design-system/primitives/Button';
import {
  LiveIndicator,
  type LiveIndicatorState
} from '@/design-system/components/LiveIndicator/LiveIndicator';
import { roleShellSpec, type ShellRole } from './roleNav';

export interface TopBarProps {
  role: ShellRole;
  /** Breadcrumb: workspace section, then the current page. */
  section?: string;
  page: string;
  /** Real-time connection pill (courier, dispatcher). */
  live?: LiveIndicatorState;
  /** Environment pill for System Admin, e.g. `Production`. */
  environment?: string;
  /** District selector label for the dispatcher, e.g. `Colombo`. */
  district?: string;
  searchPlaceholder?: string;
  onSearchClick?: () => void;
  onAssistantClick?: () => void;
  onNotificationsClick?: () => void;
  /** Shows the red dot on the bell. */
  hasUnread?: boolean;
  /** Replaces the built-in notification bell, e.g. Member 9's notification component. */
  notificationSlot?: ReactNode;
  /** Extra controls before the standard actions. */
  children?: ReactNode;
  /** Content before the breadcrumb, e.g. the brand mark where there is no sidebar. */
  leading?: ReactNode;
  className?: string;
}

/** Figma TopBar (app): 56px bar with breadcrumb, global search, language, assistant and notifications. Role changes the extras. */
export function TopBar({
  role,
  section = 'Workspace',
  page,
  live,
  environment,
  district,
  searchPlaceholder,
  onSearchClick,
  onAssistantClick,
  onNotificationsClick,
  hasUnread = false,
  notificationSlot,
  children,
  leading,
  className
}: TopBarProps) {
  const placeholder = searchPlaceholder ?? roleShellSpec[role].searchPlaceholder;
  const iconButton =
    'relative inline-flex size-9 items-center justify-center rounded-lg text-secondary transition-colors hover:bg-elevated hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus';

  return (
    <header
      data-role={role}
      className={cn(
        'sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-border-subtle bg-card px-6',
        className
      )}
    >
      {leading}
      <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-2">
        <span className="hidden text-label text-muted sm:inline">{section}</span>
        <ChevronRight aria-hidden="true" className="hidden size-3.5 text-muted sm:block" />
        <span aria-current="page" className="truncate text-body-strong text-primary">
          {page}
        </span>
      </nav>

      <div className="ml-auto flex items-center gap-1.5">
        {live ? <LiveIndicator state={live} className="hidden sm:inline-flex" /> : null}
        {environment ? (
          <span className="hidden rounded-full border border-fsm-in-transit-border bg-fsm-in-transit-bg px-2.5 py-1 text-label text-fsm-in-transit-fg sm:inline-flex">
            {environment}
          </span>
        ) : null}
        {district ? (
          <button
            type="button"
            className="hidden items-center gap-1 rounded-lg border border-border-subtle px-2.5 py-1.5 text-label text-primary hover:bg-elevated sm:inline-flex"
          >
            {district}
            <ChevronDown aria-hidden="true" className="size-3.5 text-secondary" />
          </button>
        ) : null}
        {children}
        <button
          type="button"
          onClick={onSearchClick}
          className="hidden h-9 w-[340px] max-w-[30vw] items-center gap-2 rounded-lg border border-border-subtle bg-canvas pl-3 pr-1.5 text-left text-body text-muted transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus lg:flex"
        >
          <Search aria-hidden="true" className="size-4 shrink-0" />
          <span className="min-w-0 flex-1 truncate">{placeholder}</span>
          <kbd className="rounded border border-border-subtle bg-card px-1.5 py-px text-caption text-secondary">
            ⌘K
          </kbd>
        </button>
        <button
          type="button"
          onClick={onSearchClick}
          aria-label="Search"
          className={cn(iconButton, 'lg:hidden')}
        >
          <Search aria-hidden="true" className="size-[18px]" />
        </button>
        <span className="hidden items-center gap-1 px-2 text-label text-secondary md:inline-flex">
          EN
          <ChevronDown aria-hidden="true" className="size-3" />
        </span>
        <button
          type="button"
          onClick={onAssistantClick}
          aria-label="Assistant"
          className={iconButton}
        >
          <Sparkles aria-hidden="true" className="size-[18px]" />
        </button>
        {notificationSlot ?? (
          <button
            type="button"
            onClick={onNotificationsClick}
            aria-label={hasUnread ? 'Notifications, unread' : 'Notifications'}
            className={iconButton}
          >
            <Bell aria-hidden="true" className="size-[18px]" />
            {hasUnread ? (
              <span
                aria-hidden="true"
                className="absolute right-2 top-2 size-2 rounded-full border-2 border-card bg-status-danger"
              />
            ) : null}
          </button>
        )}
      </div>
    </header>
  );
}

export interface PublicHeaderProps {
  /** Quick-track submit. */
  onTrack?: (trackingNumber: string) => void;
  onSignIn?: () => void;
  /** Pre-filled tracking number; shows search form with value when provided. */
  trackingNumber?: string;
  /** Clear handler; replaces "Track" button with "Clear" when trackingNumber is provided. */
  onClear?: () => void;
  className?: string;
}

/** Figma TopBar (Role = Public): a utility strip plus the main marketing bar with quick track. */
export function PublicHeader({
  onTrack,
  onSignIn,
  trackingNumber,
  onClear,
  className
}: PublicHeaderProps) {
  const isMobile = useMediaQuery('(max-width: 639px)');
  const hasTracking = Boolean(trackingNumber?.trim());
  const [isExpanded, setIsExpanded] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-focus input when expanded on mobile
  useEffect(() => {
    if (isMobile && isExpanded) {
      inputRef.current?.focus();
    }
  }, [isExpanded, isMobile]);

  // Close expanded state on route change (optional, but good UX)
  useEffect(() => {
    if (!isMobile) setIsExpanded(false);
  }, [isMobile]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value = String(data.get('tracking') ?? '').trim();
    if (value) onTrack?.(value);
  };

  const handleClear = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    onClear?.();
    setIsExpanded(false);
  };

  const toggleExpanded = () => {
    setIsExpanded((prev) => !prev);
  };

  const searchFormClasses = cn(
    'flex items-center gap-2 w-[352px] max-w-[30vw] transition-all duration-200 ease-out overflow-hidden',
    hasTracking
      ? 'h-10 opacity-100 max-h-20 lg:flex'
      : isMobile
        ? isExpanded
          ? 'h-10 opacity-100 max-h-20'
          : 'h-0 opacity-0 max-h-0'
        : 'hidden lg:flex'
  );

  return (
    <header className={cn('border-b border-border-subtle bg-card', className)}>
      <div className="flex items-center gap-5 bg-canvas px-10 py-1.5 text-caption text-secondary">
        <span className="truncate">Island-wide delivery · 25 districts · cash on pickup</span>
        <span className="ml-auto hidden gap-5 md:flex">
          <a href="#help">Help centre</a>
          <a href="#branch">Find a branch</a>
          <a href="#business">For business</a>
          <span>English / Sinhala / Tamil</span>
        </span>
      </div>
      <div className="flex items-center gap-7 px-10 py-3.5">
        <span className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="flex size-8 items-center justify-center rounded-lg bg-brand-primary text-brand-on"
          >
            <Package className="size-[18px]" />
          </span>
          <span className="text-h3 text-primary">SwiftDrop</span>
        </span>
        <nav aria-label="Primary" className="hidden gap-7 text-label text-primary md:flex">
          <a href="#ship">Ship</a>
          <a href="#track">Track</a>
          <a href="#pricing">Pricing</a>
          <a href="#business">Business</a>
        </nav>
        <form className={searchFormClasses} onSubmit={handleSubmit}>
          <label htmlFor="public-tracking-input" className="sr-only">
            Enter tracking number
          </label>
          <input
            ref={inputRef}
            id="public-tracking-input"
            name="tracking"
            aria-label="Enter tracking number"
            placeholder="Enter tracking number"
            defaultValue={hasTracking ? trackingNumber : undefined}
            className="min-w-0 flex-1 bg-transparent px-3 text-body text-primary outline-none placeholder:text-muted [&::-webkit-search-cancel-button]:hidden"
            disabled={hasTracking}
          />
          {hasTracking ? (
            <button
              type="button"
              onClick={handleClear}
              className="w-[72px] shrink-0 bg-brand-primary text-label text-brand-on hover:bg-brand-hover flex items-center justify-center gap-1.5"
            >
              <X className="size-3.5" aria-hidden="true" />
              <span className="text-label">Clear</span>
            </button>
          ) : (
            <button
              type="submit"
              className="w-[72px] shrink-0 bg-brand-primary text-label text-brand-on hover:bg-brand-hover"
            >
              Track
            </button>
          )}
        </form>
        {isMobile && !hasTracking && (
          <button
            type="button"
            onClick={toggleExpanded}
            aria-label={isExpanded ? 'Close search' : 'Open search'}
            aria-expanded={isExpanded}
            className="lg:hidden inline-flex items-center gap-1.5 rounded-lg border border-border-subtle bg-canvas px-3 py-1.5 text-body text-primary hover:bg-elevated transition-colors"
          >
            <Search aria-hidden="true" className="size-4" />
            <span>Track parcel</span>
          </button>
        )}
        <Button variant="ghost" density="compact" onClick={onSignIn} className="ml-auto lg:ml-0">
          Sign in
        </Button>
      </div>
    </header>
  );
}

export default TopBar;
