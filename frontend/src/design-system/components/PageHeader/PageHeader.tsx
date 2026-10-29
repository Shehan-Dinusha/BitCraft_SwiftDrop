import { ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface BreadcrumbItem {
  label: string;
  href?: string;
  onClick?: () => void;
}

export interface PageHeaderProps {
  /** Trail of parents. The last item is the current page. */
  breadcrumb?: BreadcrumbItem[];
  title: ReactNode;
  /** Badges next to the title, e.g. a status badge and speed tag. */
  badges?: ReactNode;
  subtitle?: ReactNode;
  /** Buttons on the right. */
  actions?: ReactNode;
  className?: string;
}

/** Figma PageHeader: breadcrumb, title with badges, subtitle, and an actions slot, on a card strip. */
export function PageHeader({
  breadcrumb,
  title,
  badges,
  subtitle,
  actions,
  className
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        'flex flex-col gap-3 border-b border-border-subtle bg-card px-8 py-6',
        className
      )}
    >
      {breadcrumb && breadcrumb.length > 0 ? (
        <nav aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-1.5 text-label">
            {breadcrumb.map((item, index) => {
              const isCurrent = index === breadcrumb.length - 1;
              return (
                <li key={`${item.label}-${index}`} className="flex items-center gap-1.5">
                  {isCurrent ? (
                    <span aria-current="page" className="text-primary">
                      {item.label}
                    </span>
                  ) : item.href ? (
                    <a href={item.href} className="text-secondary hover:text-primary">
                      {item.label}
                    </a>
                  ) : (
                    <button
                      type="button"
                      onClick={item.onClick}
                      className="text-secondary hover:text-primary"
                    >
                      {item.label}
                    </button>
                  )}
                  {isCurrent ? null : (
                    <ChevronRight aria-hidden="true" className="size-3.5 text-muted" />
                  )}
                </li>
              );
            })}
          </ol>
        </nav>
      ) : null}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-h1 text-primary">{title}</h1>
            {badges}
          </div>
          {subtitle ? <p className="text-body text-secondary">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}

export default PageHeader;
