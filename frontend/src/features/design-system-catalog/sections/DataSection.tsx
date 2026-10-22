import { ErgonomicActionBar, FsmStatusBadge, HighDensityDataGrid } from '@/design-system';
import type { DeliveryState } from '@/types/contracts';
import { CatalogRow, DensityPreview } from '../components/catalog-kit';

interface WebhookEvent {
  id: string;
  event: string;
  state: DeliveryState;
  attempts: number;
  lastAttempt: string;
}

const events: WebhookEvent[] = [
  { id: 'wh_001', event: 'parcel.status.changed', state: 'IN_TRANSIT', attempts: 3, lastAttempt: '09:42' },
  { id: 'wh_002', event: 'parcel.status.changed', state: 'DELIVERED', attempts: 0, lastAttempt: '09:40' },
  { id: 'wh_003', event: 'delivery.failed', state: 'FAILED', attempts: 5, lastAttempt: '09:31' },
  { id: 'wh_004', event: 'parcel.created', state: 'CREATED', attempts: 1, lastAttempt: '09:12' }
];

const columns = [
  { key: 'id' as const, label: 'Event ID' },
  { key: 'event' as const, label: 'Event' },
  {
    key: 'state' as const,
    label: 'State',
    render: (row: WebhookEvent) => <FsmStatusBadge state={row.state} />
  },
  { key: 'attempts' as const, label: 'Attempts', align: 'right' as const },
  { key: 'lastAttempt' as const, label: 'Last attempt', align: 'right' as const }
];

const noop = () => undefined;

export function DataSection() {
  return (
    <>
      <CatalogRow label="Operational grid" hint="Column-wide filter, live count, tabular numerics">
        <div className="space-y-4">
          <HighDensityDataGrid rows={events} columns={columns} label="Webhook events" />
          <HighDensityDataGrid
            rows={[]}
            columns={columns}
            label="Empty grid"
            emptyMessage="No webhook events match this filter."
          />
        </div>
      </CatalogRow>

      <CatalogRow label="Action bar by density" hint="Hick's Law cap: 2 touch, 3 comfortable, 4 compact">
        <DensityPreview>
          {(density) => (
            <ErgonomicActionBar
              density={density}
              next={
                density === 'touch'
                  ? ['ASSIGNED', 'CANCELLED']
                  : density === 'comfortable'
                    ? ['PICKED_UP', 'FAILED', 'CANCELLED']
                    : ['IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED']
              }
              onTransition={noop}
            />
          )}
        </DensityPreview>
      </CatalogRow>

      <CatalogRow label="Action bar pending" hint="Repeat taps blocked while submitting">
        <ErgonomicActionBar next={['DELIVERED', 'FAILED']} pendingState="DELIVERED" onTransition={noop} sticky />
      </CatalogRow>
    </>
  );
}
