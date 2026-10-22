# SwiftDrop Velocity components

This is the shared component contract for the SwiftDrop frontend. The library is
TypeScript-first: JSX is the syntax emitted by TSX, but every reusable component
must be authored as `.tsx` with explicit props and accessible behavior.

## Design rules

1. **Use semantic tokens, not raw colors.** Prefer `bg-card`, `text-primary`,
   `border-subtle`, `bg-brand-primary`, and `text-status-danger`. The light and
   dark themes are defined as CSS variables in `src/index.css`.
2. **Keep backend vocabulary unchanged.** `DeliveryState` and `PersonaRole`
   come from `src/types/contracts.ts`, which mirrors
   `backend/common-contracts`. Do not introduce `OUT_FOR_DELIVERY` or other
   frontend-only lifecycle values.
3. **Choose density by persona.** `ADMIN_OPS` and `MERCHANT` use `compact`,
   `COURIER` uses `touch` with 56px minimum targets, and `CUSTOMER` uses
   `comfortable`.
4. **Use `cn()` for class composition.** This keeps consumer overrides
   predictable and resolves Tailwind conflicts.
5. **Accessible states are part of the API.** Labels, focus rings,
   `aria-invalid`, `aria-describedby`, `role="dialog"`, and keyboard dismissal
   must not be removed to match a visual mockup.

## Component map

### Primitives

| Component | Purpose |
| --- | --- |
| `Button` | Primary, secondary, ghost, and danger actions. Supports normal, hover, pressed, focus, and disabled states. |
| `Input` | Unlabelled low-level input for filters and compact controls. |
| `Field` | Labelled form field with hint, error, leading, and trailing slots. Use this for forms. |
| `Card` | Semantic surface for dashboard and form sections. |
| `Modal` | Centered confirmation surface. |
| `Drawer` | Right-side detail/action surface. |
| `ModalDrawerShell` | Typed modal/drawer shell when a feature needs the same content in both layouts. |
| `Skeleton` | Loading placeholder. |

### Domain components

| Component | Use |
| --- | --- |
| `FsmStatusBadge` | High-contrast badge for one backend `DeliveryState`. |
| `DeliveryProgressStepper` | Customer-facing delivery lifecycle progress. |
| `ErgonomicActionBar` | Courier thumb-zone transition controls. Only show allowed next states from `useFsmTransition`. |
| `HighDensityDataGrid` | Compact merchant and operations table with progressive filtering. |
| `AiAssistantDock` | AI conversation panel; tool steps and citations belong in the message model. |
| `StatusBanner` | Success, warning, danger, and informational feedback. |
| `ThemeToggle` | Toggles the `dark` class on the document root. |

## Examples

```tsx
import { Button, Field, FsmStatusBadge } from '@/design-system';

<Field
  label="Recipient name"
  required
  hint="As shown on the delivery address."
  error={errors.recipientName}
  placeholder="Enter full name"
/>

<Button variant="primary" type="submit">
  Assign courier
</Button>

<FsmStatusBadge state="IN_TRANSIT" />
```

## Adding a component

1. Add the `.tsx` implementation under `src/design-system/primitives` or
   `src/design-system/components/<ComponentName>/`.
2. Define an exported props interface for non-trivial components.
3. Use semantic theme classes and `cn()`.
4. Export it from `src/design-system/index.ts`.
5. Add the component to this file, including persona/density guidance.
6. Run `npm run build` from `frontend/`.

## AI-agent guidance

When implementing a feature, agents should compose these components instead of
creating page-local buttons, fields, badges, modals, or cards. A new component
is justified only when it represents a reusable interaction or domain concept.
Never copy the raw Figma/Tailwind snapshot markup into a feature page; translate
the visual state into typed props and semantic tokens.
