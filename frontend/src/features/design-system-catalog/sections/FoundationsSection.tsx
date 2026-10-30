import { useEffect, useRef, useState } from 'react';
import {
  FsmStatusBadge,
  RoleTag,
  densityRecipes,
  type DensityMode,
  type RoleTagRole
} from '@/design-system';
import { PUBLIC_ORDER_STATUSES } from '@/types/contracts';
import { CatalogRow } from '../components/catalog-kit';

/** Shows a token's colour and its resolved value, read from the CSS variable at runtime. */
function Swatch({ token, label }: { token: string; label?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [value, setValue] = useState('');

  useEffect(() => {
    if (!ref.current) return;
    const resolved = getComputedStyle(ref.current).backgroundColor;
    const match = resolved.match(/\d+(\.\d+)?/g);
    if (!match || match.length < 3) {
      setValue(resolved);
      return;
    }
    const [r, g, b] = match.slice(0, 3).map(Number);
    const hex = `#${[r, g, b].map((part) => Math.round(part).toString(16).padStart(2, '0')).join('')}`;
    setValue(match.length > 3 && Number(match[3]) < 1 ? resolved : hex);
  }, [token]);

  return (
    <div className="flex items-center gap-3">
      <span
        ref={ref}
        aria-hidden="true"
        className="size-10 shrink-0 rounded-lg border border-border-subtle"
        style={{ backgroundColor: `var(--color-${token})` }}
      />
      <span className="flex min-w-0 flex-col">
        <span className="truncate text-label text-primary">{label ?? token}</span>
        <span className="font-mono text-caption uppercase tabular-nums text-muted">{value}</span>
      </span>
    </div>
  );
}

function SwatchGrid({ tokens }: { tokens: (string | [string, string])[] }) {
  return (
    <div className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {tokens.map((entry) =>
        typeof entry === 'string' ? (
          <Swatch key={entry} token={entry} />
        ) : (
          <Swatch key={entry[0]} token={entry[0]} label={entry[1]} />
        )
      )}
    </div>
  );
}

const roles: RoleTagRole[] = ['customer', 'merchant', 'courier', 'dispatcher', 'system-admin'];
const spacing = [4, 6, 8, 10, 12, 16, 20, 24, 32, 36, 44, 56, 64];
const radii: [string, string, string][] = [
  ['none', 'rounded-none', '0'],
  ['sm', 'rounded', '4'],
  ['md', 'rounded-lg', '8'],
  ['lg', 'rounded-xl', '12'],
  ['xl', 'rounded-2xl', '16'],
  ['full', 'rounded-full', '9999']
];
const modes: DensityMode[] = ['kinetic', 'comfortable', 'compact'];

export function FoundationsSection() {
  return (
    <>
      <CatalogRow label="Brand and text" hint="Primary actions, links, focus and body text">
        <SwatchGrid
          tokens={[
            'brand-primary',
            'brand-hover',
            'brand-pressed',
            'brand-subtle',
            'on-brand',
            'border-focus',
            'text-primary',
            'text-secondary',
            'text-muted'
          ]}
        />
      </CatalogRow>

      <CatalogRow
        label="Surfaces and borders"
        hint="Page, card and raised areas, dividers and disabled controls"
      >
        <SwatchGrid
          tokens={[
            'surface-canvas',
            'surface-card',
            'surface-elevated',
            'border-subtle',
            'disabled-bg',
            'disabled-fg'
          ]}
        />
      </CatalogRow>

      <CatalogRow label="Feedback" hint="Success, warning, danger and info">
        <SwatchGrid
          tokens={[
            'status-success',
            'status-warning',
            'status-danger',
            'status-info',
            'danger-hover',
            'danger-pressed',
            'danger-subtle'
          ]}
        />
      </CatalogRow>

      <CatalogRow
        label="Parcel statuses"
        hint="Text on a 50 tint, so contrast is at least 7:1. Pickup Assigned is a proposal that Figma does not have yet"
      >
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {PUBLIC_ORDER_STATUSES.map((status) => {
            const slug = status.toLowerCase().replace(/_/g, '-');
            return (
              <div
                key={status}
                className="flex items-center justify-between gap-3 rounded-lg border border-border-subtle bg-card p-3"
              >
                <FsmStatusBadge state={status} size="md" />
                <span className="flex gap-1" aria-hidden="true">
                  {['fg', 'bg', 'border'].map((part) => (
                    <span
                      key={part}
                      className="size-5 rounded border border-border-subtle"
                      style={{ backgroundColor: `var(--color-fsm-${slug}-${part})` }}
                    />
                  ))}
                </span>
              </div>
            );
          })}
        </div>
      </CatalogRow>

      <CatalogRow
        label="Role accents"
        hint="One colour per role, used for tags and the workspace dot"
      >
        <div className="space-y-4">
          <SwatchGrid tokens={roles.map((role): [string, string] => [`role-${role}`, role])} />
          <div className="flex flex-wrap gap-2">
            {roles.map((role) => (
              <RoleTag key={role} role={role} />
            ))}
            {roles.map((role) => (
              <RoleTag key={`solid-${role}`} role={role} variant="solid" />
            ))}
          </div>
        </div>
      </CatalogRow>

      <CatalogRow label="Spacing" hint="Pixels. Use multiples of 4 and 8 for layout">
        <div className="space-y-2">
          {spacing.map((px) => (
            <div key={px} className="flex items-center gap-3">
              <span className="w-10 text-right font-mono text-caption tabular-nums text-muted">
                {px}
              </span>
              <span className="h-3 rounded-sm bg-brand-primary" style={{ width: px * 4 }} />
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Radius and elevation">
        <div className="space-y-5">
          <div className="flex flex-wrap gap-4">
            {radii.map(([name, className, px]) => (
              <div key={name} className="flex flex-col items-center gap-2">
                <span
                  className={`size-16 border-2 border-brand-primary bg-brand-subtle ${className}`}
                />
                <span className="text-caption text-secondary">
                  {name} · {px}
                </span>
              </div>
            ))}
          </div>
          <div className="flex flex-wrap gap-6">
            <div className="flex h-20 w-44 items-center justify-center rounded-xl bg-card text-caption text-secondary shadow-ambient">
              elevation-lg (ambient)
            </div>
            <div className="flex h-20 w-44 items-center justify-center rounded-xl bg-card text-caption text-secondary shadow-overlay">
              overlay
            </div>
          </div>
        </div>
      </CatalogRow>

      <CatalogRow
        label="Density modes"
        hint="Courier uses Kinetic, customer Comfortable, everyone else Compact"
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] text-left text-body">
            <thead>
              <tr className="border-b border-border-subtle text-overline uppercase text-secondary">
                <th className="py-2 pr-4">Mode</th>
                <th className="py-2 pr-4">Min target</th>
                <th className="py-2 pr-4">Control</th>
                <th className="py-2 pr-4">Radius</th>
                <th className="py-2">Max actions</th>
              </tr>
            </thead>
            <tbody>
              {modes.map((mode) => {
                const recipe = densityRecipes[mode];
                return (
                  <tr key={mode} className="border-b border-border-subtle">
                    <td className="py-2 pr-4 text-body-strong capitalize text-primary">{mode}</td>
                    <td className="py-2 pr-4 font-mono tabular-nums">{recipe.minTarget}px</td>
                    <td className="py-2 pr-4 font-mono text-caption text-secondary">
                      {recipe.control}
                    </td>
                    <td className="py-2 pr-4 font-mono text-caption text-secondary">
                      {recipe.radius}
                    </td>
                    <td className="py-2 font-mono tabular-nums">{recipe.maxActions}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </CatalogRow>
    </>
  );
}
