import { ArrowRight, Plus, Trash2 } from 'lucide-react';
import { Button, IconButton, type ButtonState, type ButtonVariant } from '@/design-system';
import { CatalogRow, DemoRow, DensityPreview } from '../components/catalog-kit';

const variants: ButtonVariant[] = ['primary', 'secondary', 'outline', 'ghost', 'destructive'];
const states: ButtonState[] = ['default', 'hover', 'pressed', 'focused', 'disabled', 'loading'];

const stateLabels: Record<ButtonState, string> = {
  default: 'Default',
  hover: 'Hover',
  pressed: 'Pressed',
  focused: 'Focused',
  disabled: 'Disabled',
  loading: 'Loading'
};

export function ButtonsSection() {
  return (
    <>
      <CatalogRow label="Variants" hint="Each variant at its resting state">
        <DemoRow>
          {variants.map((variant) => (
            <Button key={variant} variant={variant}>
              {variant.charAt(0).toUpperCase() + variant.slice(1)}
            </Button>
          ))}
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="States" hint="Forced with the `state` prop; product code omits it">
        <div className="space-y-2">
          {variants.map((variant) => (
            <div key={variant} className="flex flex-wrap items-center gap-2">
              <span className="w-20 shrink-0 text-[10px] uppercase tracking-wider text-muted">
                {variant}
              </span>
              {states.map((state) => (
                <Button key={state} variant={variant} state={state} loadingLabel="Saving">
                  {stateLabels[state]}
                </Button>
              ))}
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Density" hint="44px comfortable controls by default">
        <DensityPreview>
          {() => (
            <DemoRow>
              <Button>Continue</Button>
              <Button variant="secondary">Save draft</Button>
              <Button variant="destructive" leftIcon={<Trash2 />}>
                Delete
              </Button>
            </DemoRow>
          )}
        </DensityPreview>
      </CatalogRow>

      <CatalogRow label="Icon button" hint="Square, icon only; aria-label is required">
        <DensityPreview>
          {() => (
            <DemoRow>
              {variants.map((variant) => (
                <IconButton
                  key={variant}
                  variant={variant}
                  icon={<Plus />}
                  aria-label={`Add (${variant})`}
                />
              ))}
              <IconButton variant="primary" icon={<Plus />} aria-label="Add (disabled)" disabled />
            </DemoRow>
          )}
        </DensityPreview>
      </CatalogRow>

      <CatalogRow label="Icons and width">
        <DemoRow>
          <Button leftIcon={<Plus />}>New order</Button>
          <Button variant="outline" rightIcon={<ArrowRight />}>
            Next step
          </Button>
          <Button variant="secondary" disabled>
            Unavailable
          </Button>
        </DemoRow>
        <Button className="mt-3 w-full">Full width action</Button>
      </CatalogRow>
    </>
  );
}
