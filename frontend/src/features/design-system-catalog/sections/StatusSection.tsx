import {
  FsmStatusBadge,
  StatusBanner,
  deliveryStateColors,
  slaColors,
  type FsmStatusBadgeSize,
  type StatusBannerTone
} from '@/design-system';
import type { DeliveryState } from '@/types/contracts';
import { cn } from '@/utils/cn';
import { CatalogRow, DemoGrid, DemoRow } from '../components/catalog-kit';

const states: DeliveryState[] = ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED'];
const sizes: FsmStatusBadgeSize[] = ['sm', 'md', 'lg'];
const tones: StatusBannerTone[] = ['success', 'warning', 'danger', 'info'];

const toneCopy: Record<StatusBannerTone, { title: string; body: string }> = {
  success: { title: 'Delivery completed', body: 'Proof of delivery was captured and verified.' },
  warning: { title: 'SLA at risk', body: 'Three parcels in Zone 4 are close to their promised window.' },
  danger: { title: 'Replay failed', body: 'Webhook #wh_001 exhausted its retries and moved to the DLQ.' },
  info: { title: 'Courier reassigned', body: 'Zone 4 coverage moved to Amara Okafor for the next 2 hours.' }
};

function SlaPill({ tone, label }: { tone: keyof typeof slaColors; label: string }) {
  const tokens = slaColors[tone];
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold',
        tokens.background,
        tokens.foreground,
        tokens.border
      )}
    >
      {label}
    </span>
  );
}

export function StatusSection() {
  return (
    <>
      <CatalogRow label="FSM badge sizes" hint="Density picks the default size">
        <div className="space-y-3">
          {sizes.map((size) => (
            <DemoRow key={size}>
              <span className="w-8 shrink-0 text-[10px] uppercase tracking-wider text-muted">{size}</span>
              {states.map((state) => (
                <FsmStatusBadge key={state} state={state} size={size} />
              ))}
            </DemoRow>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Muted tone" hint="Upcoming steps keep the vocabulary without the palette">
        <DemoRow>
          {states.map((state) => (
            <FsmStatusBadge key={state} state={state} tone="muted" />
          ))}
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="SLA tokens" hint="Composed from `slaColors`; no page invents these colors">
        <DemoRow>
          <SlaPill tone="warning" label="Due in 8 min" />
          <SlaPill tone="critical" label="Overdue by 4 min" />
          {states.map((state) => (
            <span
              key={state}
              className={cn(
                'size-3 rounded-full border',
                deliveryStateColors[state].background,
                deliveryStateColors[state].border
              )}
              title={state}
            />
          ))}
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Status banners">
        <div className="space-y-3">
          {tones.map((tone) => (
            <StatusBanner key={tone} tone={tone} title={toneCopy[tone].title}>
              {toneCopy[tone].body}
            </StatusBanner>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Banner with action and dismiss">
        <DemoGrid>
          <StatusBanner
            tone="warning"
            title="Courier unresponsive"
            action={
              <button type="button" className="text-sm font-semibold underline">
                Reassign parcel
              </button>
            }
            onDismiss={() => undefined}
          >
            Ravi Menon has not moved for 6 minutes.
          </StatusBanner>
          <StatusBanner tone="danger" title="Payment failed" onDismiss={() => undefined}>
            The merchant card was declined. Retry from billing settings.
          </StatusBanner>
        </DemoGrid>
      </CatalogRow>
    </>
  );
}
