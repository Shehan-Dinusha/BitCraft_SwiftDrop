import { Field, Input, type InputState } from '@/design-system';
import { CatalogRow, DemoGrid, DemoRow, DensityPreview } from '../components/catalog-kit';

const states: InputState[] = ['default', 'hover', 'focused', 'filled', 'error', 'disabled', 'readOnly'];

export function FormControlsSection() {
  return (
    <>
      <CatalogRow label="Input archetypes" hint="`text`, `password`, `search`, `currency`, `phone`">
        <DemoGrid>
          <Input placeholder="Recipient name" aria-label="Text input" />
          <Input variant="password" defaultValue="swiftdrop" aria-label="Password input" />
          <Input variant="search" defaultValue="parcel 4821" onClear={() => undefined} aria-label="Search input" />
          <Input variant="currency" defaultValue="24.50" aria-label="Currency input" />
          <Input variant="phone" defaultValue="555 018 2244" aria-label="Phone input" />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Input states" hint="Forced with the `state` prop">
        <DemoGrid>
          {states.map((state) => (
            <Input
              key={state}
              state={state}
              placeholder={state}
              defaultValue={state === 'default' || state === 'disabled' ? undefined : 'Value'}
              aria-label={`${state} input`}
            />
          ))}
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Field" hint="Label, hint, and error wiring">
        <DemoGrid>
          <Field label="Recipient name" placeholder="Enter full name" />
          <Field label="Delivery notes" hint="Left with the concierge if nobody answers." placeholder="Optional" />
          <Field label="Phone" required variant="phone" error="Enter a valid contact number." defaultValue="555" />
          <Field label="Tracking ID" value="SD-4821-XZ" readOnly />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Density">
        <DensityPreview>
          {() => (
            <DemoRow className="items-start">
              <Field label="Parcel weight" hint="Kilograms" placeholder="2.5" className="max-w-xs" />
              <Input placeholder="Unlabelled filter" aria-label="Unlabelled filter" className="max-w-xs" />
            </DemoRow>
          )}
        </DensityPreview>
      </CatalogRow>
    </>
  );
}
