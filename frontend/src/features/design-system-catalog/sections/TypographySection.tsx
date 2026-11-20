import { textStyles } from '@/design-system';
import { cn } from '@/utils/cn';
import { CatalogRow } from '../components/catalog-kit';

const names: Record<keyof typeof textStyles, string> = {
  display: 'Display',
  h1: 'H1',
  h2: 'H2',
  h3: 'H3',
  bodyL: 'Body L',
  body: 'Body',
  bodyStrong: 'Body strong',
  label: 'Label',
  caption: 'Caption',
  overline: 'Overline',
  mono: 'Mono',
  monoL: 'Mono L'
};

export function TypographySection() {
  return (
    <CatalogRow label="Type scale" hint="Text styles from Figma">
      <div className="space-y-4">
        {(Object.keys(textStyles) as (keyof typeof textStyles)[]).map((key) => {
          const style = textStyles[key];
          return (
            <div
              key={key}
              className="grid items-baseline gap-1 border-b border-border-subtle pb-3 sm:grid-cols-[10rem_1fr]"
            >
              <div>
                <p className="text-label text-primary">{names[key]}</p>
                <p className="text-caption text-muted">{style.spec}</p>
              </div>
              <p className={cn('text-primary', style.className)}>
                {key === 'mono' || key === 'monoL' ? 'SD-7K4M-9QX2 · LKR 1,250.00' : 'Deliver it faster'}
              </p>
            </div>
          );
        })}
      </div>
    </CatalogRow>
  );
}
