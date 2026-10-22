import {
  DeliveryProgressStepper,
  SlideToTransitionBar,
  type SlideToTransitionBarState
} from '@/design-system';
import type { DeliveryState } from '@/types/contracts';
import { CatalogRow, DemoGrid, DemoRow, DensityPreview } from '../components/catalog-kit';

const states: DeliveryState[] = [
  'CREATED',
  'ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'DELIVERED',
  'FAILED',
  'CANCELLED'
];

const slideStates: SlideToTransitionBarState[] = [
  'idle',
  'dragging',
  'armed',
  'committed',
  'disabled'
];

const slideLabels: Record<SlideToTransitionBarState, string> = {
  idle: 'Idle',
  dragging: 'Dragging',
  armed: 'Armed past 50%',
  committed: 'Committed',
  disabled: 'Disabled'
};

export function ProgressSection() {
  return (
    <>
      <CatalogRow
        label="Delivery stepper"
        hint="Solid = reached, muted = upcoming, green connectors"
      >
        <div className="space-y-4">
          {states.map((state) => (
            <div key={state} className="space-y-1">
              <span className="text-[10px] uppercase tracking-wider text-muted">{state}</span>
              <DeliveryProgressStepper current={state} />
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Slide to transition" hint="Forced states; release at 85% to commit">
        <div className="space-y-3">
          {slideStates.map((state) => (
            <div key={state} className="space-y-1">
              <span className="text-[10px] uppercase tracking-wider text-muted">
                {slideLabels[state]}
              </span>
              <SlideToTransitionBar
                label="Slide to confirm pickup"
                confirmLabel="Release to confirm pickup"
                state={state}
                onConfirm={() => undefined}
              />
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Live slide bar" hint="Drag past 50% to arm, 85% to commit">
        <DemoGrid>
          <SlideToTransitionBar label="Slide to start trip" onConfirm={() => undefined} />
          <SlideToTransitionBar label="Slide to confirm pickup" onConfirm={() => undefined} />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Slide density" hint="64px touch track, 56px thumb">
        <DensityPreview>
          {() => (
            <DemoRow>
              <SlideToTransitionBar
                label="Slide to deliver"
                onConfirm={() => undefined}
                className="max-w-md"
              />
            </DemoRow>
          )}
        </DensityPreview>
      </CatalogRow>
    </>
  );
}
