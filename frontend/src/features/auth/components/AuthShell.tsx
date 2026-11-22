import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from '@/design-system/components/BrandMark/BrandMark';
import { DensityProvider } from '@/design-system/providers/DensityProvider';
import { googleAuthorizeUrl } from '@/services/api/authApi';
import { cn } from '@/utils/cn';
import { mix, SkyBackdrop } from './SkyBackdrop';

/**
 * Filled field style for the auth screens: the design-system `Input`, with the border softened into a
 * filled surface. Focus, error and disabled states still come from the component.
 */
export const authFieldClass =
  'border-transparent bg-elevated hover:border-border-subtle focus-within:bg-card';


/** Muted leading icon inside a field. */
export function FieldIcon({ children }: { children: ReactNode }) {
  return <span className="flex text-muted [&_svg]:size-[18px]">{children}</span>;
}

/** Visually hidden label: the placeholder-led design stays accessible to screen readers. */
export function SrLabel({ htmlFor, children }: { htmlFor: string; children: ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="sr-only">
      {children}
    </label>
  );
}

/** "· · · · Or sign in with · · · ·" */
export function AuthDivider({ label }: { label: string }) {
  return (
    <div className="my-6 flex items-center gap-3" role="separator" aria-label={label}>
      <span className="h-0 flex-1 border-t-2 border-dotted border-border-subtle" />
      <span className="text-caption text-muted">{label}</span>
      <span className="h-0 flex-1 border-t-2 border-dotted border-border-subtle" />
    </div>
  );
}

/** White social tile. Google needs a full-page redirect, so this is a link, not a fetch. */
export function GoogleTile({ label = 'Continue with Google' }: { label?: string }) {
  return (
    <a
      href={googleAuthorizeUrl()}
      className="flex h-12 w-full items-center justify-center gap-3 rounded-xl border border-border-subtle bg-card text-body-strong text-primary shadow-ambient transition-all hover:-translate-y-px hover:border-brand-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 48 48"
        className="size-5 shrink-0"
        aria-hidden="true"
      >
        <path
          fill="#EA4335"
          d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
        />
        <path
          fill="#4285F4"
          d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
        />
        <path
          fill="#FBBC05"
          d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
        />
        <path
          fill="#34A853"
          d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
        />
        <path fill="none" d="M0 0h48v48H0z" />
      </svg>
      {label}
    </a>
  );
}

/** Text link in the brand colour. */
export function AuthLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="rounded font-semibold text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
    >
      {children}
    </Link>
  );
}

export interface AuthShellProps {
  /** Icon shown in the tile at the top of the card (e.g. `<LogIn />`). */
  icon: ReactNode;
  /** Optional tone for the icon tile, e.g. success on the "registered" page. */
  iconTone?: 'default' | 'success';
  title: string;
  description?: ReactNode;
  children: ReactNode;
  /** Line under the card, e.g. "Don't have an account? Create one". */
  below?: ReactNode;
  /** `md` (460px) or `lg` (540px, the sign-up form). */
  width?: 'md' | 'lg';
}

/**
 * Sky-style frame for every auth screen: a soft brand-tinted sky, faint rings and cloud glows behind
 * a glassy card with an icon tile, centred title and the form. Customer-facing, so `comfortable`
 * density throughout.
 */
export function AuthShell({
  icon,
  iconTone = 'default',
  title,
  description,
  children,
  below,
  width = 'md'
}: AuthShellProps) {
  return (
    <DensityProvider density="comfortable">
      <div className="relative flex min-h-screen flex-col overflow-hidden bg-canvas text-primary">
        <SkyBackdrop />

        <header className="relative px-5 py-6 sm:px-10 sm:py-8">
          <Link
            to="/"
            aria-label="SwiftDrop home"
            className="inline-flex items-center gap-2.5 rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
          >
            <BrandMark size={40} />
            <span className="text-h2 text-primary">SwiftDrop</span>
          </Link>
        </header>

        <main className="relative flex flex-1 flex-col items-center justify-center px-4 pb-14">
          <section
            className={cn(
              'w-full rounded-3xl border p-7 shadow-overlay backdrop-blur-md sm:p-10',
              width === 'lg' ? 'max-w-[540px]' : 'max-w-[460px]'
            )}
            style={{
              borderColor: mix('--color-surface-card', 85),
              background: `linear-gradient(180deg, ${mix('--color-brand-primary', 12, 'var(--color-surface-card)')} 0%, var(--color-surface-card) 42%)`
            }}
          >
            <div className="flex flex-col items-center text-center">
              <span
                aria-hidden="true"
                className={cn(
                  'flex size-14 items-center justify-center rounded-2xl border shadow-ambient [&_svg]:size-6',
                  iconTone === 'success'
                    ? 'border-fsm-delivered-border bg-fsm-delivered-bg text-fsm-delivered-fg'
                    : 'border-border-subtle bg-card text-primary'
                )}
              >
                {icon}
              </span>
              <h1 className="mt-6 text-h1 tracking-tight text-primary">{title}</h1>
              {description ? (
                <p className="mt-2 max-w-sm text-body-l text-secondary">{description}</p>
              ) : null}
            </div>

            <div className="mt-8">{children}</div>
          </section>

          {below ? <p className="mt-6 text-center text-body text-secondary">{below}</p> : null}
        </main>
      </div>
    </DensityProvider>
  );
}

export default AuthShell;
