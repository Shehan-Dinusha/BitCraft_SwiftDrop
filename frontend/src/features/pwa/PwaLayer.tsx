import { useState, type ReactNode } from 'react';
import { Download, RefreshCw, Share, WifiOff, X } from 'lucide-react';
import { useRegisterSW } from 'virtual:pwa-register/react';
import { BrandMark, Button } from '@/design-system';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';
import {
  isIos,
  isStandalone,
  rememberInstallDismissed,
  useDeferredInstallPrompt,
  useOnline,
  wasInstallDismissed
} from './pwaSupport';

/** Roles that work from a phone and benefit from a home-screen app. Ops and merchants use desktops. */
const INSTALL_ROLES: ReadonlySet<PersonaRole> = new Set(['COURIER', 'CUSTOMER']);

const UPDATE_CHECK_MS = 60 * 60 * 1000;

function PromptCard({
  icon,
  title,
  children,
  onClose
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
  onClose?: () => void;
}) {
  return (
    <div
      role="status"
      className="pointer-events-auto flex w-full items-start gap-3 rounded-xl border border-border-subtle bg-card p-3 shadow-ambient"
    >
      <span aria-hidden="true" className="mt-0.5 shrink-0">
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-body-strong text-primary">{title}</p>
        <div className="pt-1 text-body text-secondary">{children}</div>
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Dismiss"
          className="inline-flex size-8 shrink-0 items-center justify-center rounded-lg text-muted hover:bg-elevated hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
        >
          <X className="size-4" />
        </button>
      )}
    </div>
  );
}

/** "A new version is ready". Waits for the user, so a courier is never reloaded mid-delivery. */
function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      // Long-lived installed apps rarely navigate; check for a new build every hour.
      if (registration) setInterval(() => void registration.update(), UPDATE_CHECK_MS);
    }
  });

  if (!needRefresh) return null;
  return (
    <PromptCard
      icon={<RefreshCw className="size-5 text-brand-primary" />}
      title="A new version of SwiftDrop is ready"
      onClose={() => setNeedRefresh(false)}
    >
      <p>Reload when you are not in the middle of something.</p>
      <Button density="compact" className="mt-2" onClick={() => void updateServiceWorker(true)}>
        Reload
      </Button>
    </PromptCard>
  );
}

/** "Install SwiftDrop" for signed-in couriers and customers. */
function InstallPrompt() {
  const token = useAuthStore((state) => state.token);
  const role = useAuthStore((state) => state.role);
  const { deferred, installed, clear } = useDeferredInstallPrompt();
  const [dismissed, setDismissed] = useState(wasInstallDismissed);

  const eligible = Boolean(token) && INSTALL_ROLES.has(role) && !dismissed && !installed && !isStandalone();
  const ios = isIos();
  if (!eligible || (!deferred && !ios)) return null;

  const dismiss = () => {
    rememberInstallDismissed();
    setDismissed(true);
  };

  const install = async () => {
    if (!deferred) return;
    await deferred.prompt();
    const { outcome } = await deferred.userChoice;
    clear();
    if (outcome === 'dismissed') dismiss();
  };

  return (
    <PromptCard icon={<BrandMark size={24} />} title="Install SwiftDrop" onClose={dismiss}>
      {deferred ? (
        <>
          <p>
            {role === 'COURIER'
              ? 'Open your deliveries from the home screen, full screen and fast on a weak signal.'
              : 'Track your parcels from the home screen with one tap.'}
          </p>
          <Button
            density="compact"
            className="mt-2"
            leftIcon={<Download className="size-4" />}
            onClick={() => void install()}
          >
            Install
          </Button>
        </>
      ) : (
        <p>
          Tap <Share aria-label="Share" className="inline size-4 align-text-bottom" /> Share, then{' '}
          <span className="font-semibold text-primary">Add to Home Screen</span>.
        </p>
      )}
    </PromptCard>
  );
}

/** Offline indicator, update prompt and install prompt, pinned to the top so they never cover the thumb-zone action bars. */
export function PwaLayer() {
  const online = useOnline();
  return (
    <div className="pointer-events-none fixed inset-x-0 top-0 z-50 mx-auto flex w-full max-w-md flex-col items-center gap-2 px-4 pt-3">
      {!online && (
        <p
          role="status"
          className="pointer-events-auto inline-flex items-center gap-2 rounded-full bg-status-danger px-3 py-1.5 text-label text-brand-on shadow-ambient"
        >
          <WifiOff aria-hidden="true" className="size-4" />
          You're offline. Changes won't be sent until you reconnect.
        </p>
      )}
      <UpdatePrompt />
      <InstallPrompt />
    </div>
  );
}
