import {
  LiveIndicator,
  STATUS_PROGRESS_STEPS,
  StatusProgress,
  StatusTimeline,
  TimelineStep,
  type TimelineStepState
} from '@/design-system';
import { CatalogRow, DemoRow } from '../components/catalog-kit';

const stepStates: TimelineStepState[] = ['done', 'current', 'upcoming', 'failed'];

export function TimelineSection() {
  return (
    <>
      <CatalogRow label="Timeline step" hint="Done, current, upcoming, failed">
        <ol className="grid max-w-md gap-0">
          {stepStates.map((state, index) => (
            <TimelineStep
              key={state}
              state={state}
              title={state === 'failed' ? 'Delivery attempt failed' : 'Picked Up'}
              meta="26 Oct 2026, 14:32 · Colombo"
              note={
                state === 'failed' ? 'Recipient unavailable. Next attempt tomorrow.' : undefined
              }
              isLast={index === stepStates.length - 1}
            />
          ))}
        </ol>
      </CatalogRow>

      <CatalogRow label="Status timeline" hint="Tracking history card">
        <StatusTimeline
          className="max-w-md"
          steps={[
            { state: 'done', title: 'Created', meta: '25 Oct 2026, 09:10 · Gampaha' },
            { state: 'done', title: 'Picked Up', meta: '25 Oct 2026, 15:42 · Gampaha' },
            {
              state: 'current',
              title: 'In Transit',
              meta: '26 Oct 2026, 08:05 · Gampaha → Colombo'
            },
            { state: 'upcoming', title: 'Out for Delivery', meta: 'Expected 27 Oct' },
            { state: 'upcoming', title: 'Delivered' }
          ]}
        />
      </CatalogRow>

      <CatalogRow label="Status progress" hint="The five public steps; one row per current status">
        <div className="space-y-6">
          {STATUS_PROGRESS_STEPS.map((step) => (
            <StatusProgress key={step.status} current={step.status} />
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Live indicator">
        <DemoRow>
          <LiveIndicator state="live" />
          <LiveIndicator state="reconnecting" />
          <LiveIndicator state="offline" queued={3} />
          <LiveIndicator state="offline" />
        </DemoRow>
      </CatalogRow>
    </>
  );
}
