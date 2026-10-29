# SwiftDrop Velocity components

This is the shared component contract for the SwiftDrop frontend. The library is
TypeScript-first: JSX is the syntax emitted by TSX, but every reusable component
must be authored as `.tsx` with explicit props and accessible behavior.

## Design rules

1. **Use semantic tokens, not raw colors.** Prefer `bg-card`, `text-primary`,
   `border-border-subtle`, `bg-brand-primary`, and `text-status-danger`. The
   theme is **light only** (a product decision) and is defined as CSS variables
   in `src/index.css`, mirroring the Figma "Velocity / Theme" collection. Do not
   add `dark:` variants. Note that
   nested palette keys are flattened: the border color is `border-border-subtle`
   (not `border-subtle`), and the focus color is `outline-border-focus`.
2. **Keep backend vocabulary unchanged.** `DeliveryState` and `PersonaRole`
   come from `src/types/contracts.ts`, which mirrors
   `backend/common-contracts`. `OUT_FOR_DELIVERY` and `RETURNED_TO_SENDER` are
   public order statuses; do not introduce other frontend-only lifecycle values.
3. **Choose density by persona.** `ADMIN_OPS` and `MERCHANT` use `compact`,
   `COURIER` uses `kinetic` (formerly `touch`, kept as an alias) with 56px
   minimum targets, and `CUSTOMER` uses `comfortable`. These are the Figma
   "Velocity / Density" modes Kinetic, Comfortable and Compact.
4. **Use `cn()` for class composition.** This keeps consumer overrides
   predictable and resolves Tailwind conflicts.
5. **Accessible states are part of the API.** Labels, focus rings,
   `aria-invalid`, `aria-describedby`, `role="dialog"`, and keyboard dismissal
   must not be removed to match a visual mockup.
6. **Typography is loaded, not assumed.** `index.html` preconnects to Google
   Fonts and loads Plus Jakarta Sans (UI) and JetBrains Mono. Use `font-mono`
   for tracking IDs, prices, coordinates, and ETAs; never hardcode a font stack
   in a component.

## Component map

### Primitives

| Component          | Purpose                                                                                                                                                                                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Button`           | Primary, secondary, outline, ghost, and destructive actions. Renders at the active persona density and supports default, hover, pressed, focused, disabled, and loading states.                                                                         |
| `Input`            | Bare text control for filters and compact controls. Five archetypes (`text`, `password`, `search`, `currency`, `phone`) and seven visual states.                                                                                                        |
| `Field`            | Labelled form field built on `Input`, with hint and error messaging. Use this for forms.                                                                                                                                                                |
| `SurfaceCard`      | Card surface with `base`, `elevated`, `interactive`, and `selected` roles, plus header/actions/body/footer slots.                                                                                                                                       |
| `Card`             | Alias for `SurfaceCard` with the base role, kept for existing feature pages.                                                                                                                                                                            |
| `Modal`            | Centered dialog. Thin wrapper over `ModalDrawerShell`.                                                                                                                                                                                                  |
| `Drawer`           | Right-side dialog. Thin wrapper over `ModalDrawerShell`.                                                                                                                                                                                                |
| `ModalDrawerShell` | Typed dialog shell shared by modal and drawer layouts: title, description, header, scrollable body, and pinned footer.                                                                                                                                  |
| `Skeleton`         | Loading placeholder. `shape` is `text`, `block`, or `circle`; `lines` renders stacked text bars; `preset` is `text-block`, `table-row` or `card` (the Figma layouts). Always decorative (`aria-hidden`), so put `aria-busy` on the region being loaded. |

### Domain components

| Component                 | Use                                                                                                                                                                                                                                                                                    |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FsmStatusBadge`          | Token-driven pill for one backend `DeliveryState`: status dot plus uppercase label.                                                                                                                                                                                                    |
| `DeliveryProgressStepper` | Customer-facing delivery lifecycle progress.                                                                                                                                                                                                                                           |
| `ErgonomicActionBar`      | Courier thumb-zone transition controls. Only show allowed next states from `useFsmTransition`; the first action is primary, `FAILED`/`CANCELLED` are destructive, and the recipe caps the visible count (2 touch, 3 comfortable, 4 compact). Pass `pendingState` to block repeat taps. |
| `SlideToTransitionBar`    | Slide-to-confirm control for courier state transitions, with an Enter/Space fallback.                                                                                                                                                                                                  |
| `HighDensityDataGrid`     | Compact merchant and operations table with progressive filtering, a live row count, right-aligned numeric columns, and an empty state.                                                                                                                                                 |
| `AiAssistantDock`         | AI conversation panel; citations are attributed per message. `onSubmit` may return the assistant reply.                                                                                                                                                                                |
| `StatusBanner`            | Success, warning, danger, and informational feedback on the tinted `status-*-bg` / `status-*-fg` token triads. `danger` announces as `role="alert"`.                                                                                                                                   |
| `SelectDropdown`          | Single-choice select with listbox keyboard support, optional search, and status badges.                                                                                                                                                                                                |
| `DropdownMenu`            | Floating option panel used by `SelectDropdown` and by action menus.                                                                                                                                                                                                                    |
| `DropdownItem`            | One option row inside a `DropdownMenu`.                                                                                                                                                                                                                                                |

### Velocity v2 components (from the new Figma)

Source file and node ids: [figma-source.md](../docs/velocity-design-system/figma-source.md).
Figma is the source of truth; every component below has a section in `/catalog`.

| Figma section                      | Components                                                                                                                                                                                                          |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Foundations                        | tokens in `src/index.css` and `tokens/swiftdrop-velocity-tokens.json`, text styles (`text-display` ... `text-mono-l`, `velocityTextStyles`)                                                                         |
| 01 Domain tags                     | `RoleTag`, `SpeedTag`, `QueueChip`, `CashState`                                                                                                                                                                     |
| 02 Identifiers and money           | `TrackingNumber`, `MoneyAmount`                                                                                                                                                                                     |
| 03 Status timeline                 | `TimelineStep`, `StatusTimeline`, `StatusProgress`, `LiveIndicator`                                                                                                                                                 |
| 04 Feedback                        | `Alert` (use for new work; `StatusBanner` stays for existing pages), `Toast` with `ToastProvider` and `useToast`, `EmptyState`, `Skeleton` presets                                                                  |
| 05 Page structure                  | `Tabs` and `TabItem`, `PageHeader`                                                                                                                                                                                  |
| 06 Navigation                      | `NavItem`, `SideNav`, `TopBar`, `PublicHeader`, `BottomNav`                                                                                                                                                         |
| 07 Order patterns                  | `PriceBreakdown` (customer, merchant, recalculated), `OrderCard` (customer, courier pickup, courier delivery)                                                                                                       |
| 08 Inputs and selection            | `Checkbox`, `Radio`, `Toggle`, `Textarea`, `RadioCard`, `ChoiceChip`, `SpecialField`, `IconButton`, `PhotoUpload`, `SlotChip`, `PickupSlotPicker`                                                                   |
| 09 Data tables                     | `TableHeaderCell`, `OrderTable` and `OrderTableRow`, `FilterBar`, `Pagination`; `HighDensityDataGrid` keeps the simpler generic grid                                                                                |
| 11 Cards, sheets, special controls | `BottomSheet`, `SlideToConfirm` (new work; `SlideToTransitionBar` stays for existing pages), `AssistantLauncher`, `AssistantPanel`, `AiAssistant` (`AiAssistantDock` stays), `NotificationItem`, `NotificationTray` |

Rules of thumb:

- Build with the semantic (Theme) tokens, never the primitive palette.
- Pages should move to these components gradually, each member in their own
  PR. The old components are not removed.
- `SlideToConfirm` takes `holdMs` for the optional "keep holding" step. It
  defaults to `0` (release past 85% confirms), which matches the old bar.

## Button states and density

`Button` reads the active persona density from `DensityProvider` and renders the
matching target height, padding, icon size, radius, and label size from
`densityRecipes`. Pass `density` to override it for a single control.

| Density       | Target height | Padding     | Icon | Radius |
| ------------- | ------------- | ----------- | ---- | ------ |
| `kinetic`     | 56px          | 20px / 16px | 24px | 12px   |
| `comfortable` | 44px          | 16px / 12px | 20px | 8px    |
| `compact`     | 34px          | 12px / 8px  | 18px | 6px    |

`touch` is a deprecated alias of `kinetic`; both resolve to the same recipe.

`state` renders one explicit visual state instead of the live
`:hover`/`:active`/`:focus-visible` behaviour. Use it for design reviews and
catalog screens, never to fake interaction state in product views. `loading`
renders a spinner, sets `aria-busy`, and disables the control.

```tsx
<Button variant="primary" leftIcon={<Plus size={16} />} loading>
  Assign courier
</Button>

<Button variant="destructive" density="kinetic" state="hover">
  Cancel delivery
</Button>
```

## Form controls

`Input` is the shared control behind `Field`. It renders a border wrapper with a
leading slot, the native input, and a trailing slot, so archetype affordances
and state icons stay consistent across every form.

| Archetype  | Behaviour                                                                                           |
| ---------- | --------------------------------------------------------------------------------------------------- |
| `text`     | Native text input.                                                                                  |
| `password` | Masked input with a reveal toggle in the trailing slot.                                             |
| `search`   | `inputMode="search"` with a magnifier leading icon; pass `onClear` to render a clear button.        |
| `currency` | `inputMode="decimal"` with a currency prefix and monospaced digits. Use `tabular-nums` for amounts. |
| `phone`    | `type="tel"` with a dialling-code prefix and monospaced digits.                                     |

| State      | Treatment                                                          |
| ---------- | ------------------------------------------------------------------ |
| `default`  | `bg-card`, `border-border-subtle`, placeholder `text-muted`.       |
| `hover`    | Border darkens to `border-secondary`.                              |
| `focused`  | Inset 2px `outline-border-focus` ring on the wrapper.              |
| `filled`   | Value is `text-primary`; the border stays subtle.                  |
| `error`    | `border-status-danger`, `aria-invalid`, and a trailing alert icon. |
| `disabled` | `bg-disabled-bg` / `text-disabled-fg`.                             |
| `readOnly` | `bg-canvas` with a trailing lock icon.                             |

`state` forces one explicit visual state for catalog and review screens. In
product views, omit it and let `invalid`, `disabled`, and `readOnly` drive the
appearance.

```tsx
<Field
  label="Recipient phone"
  variant="phone"
  required
  error={errors.phone}
  placeholder="555 0100"
/>

<Input variant="currency" currency="$" placeholder="0.00" aria-label="Delivery fee" />
```

`SelectDropdown` follows the WAI-ARIA combobox pattern: the trigger is a
`role="combobox"` button, the panel is a `role="listbox"`, and the active row is
tracked with `aria-activedescendant` so focus never leaves the trigger.

| Key                     | Behaviour                                       |
| ----------------------- | ----------------------------------------------- |
| `ArrowDown` / `ArrowUp` | Open the menu, then move the active row.        |
| `Home` / `End`          | Jump to the first or last row.                  |
| `Enter` / `Space`       | Open, then select the active row.               |
| `Escape`                | Close the menu and return focus to the trigger. |
| `Tab`                   | Close the menu and move on.                     |

```tsx
<SelectDropdown
  label="Delivery status"
  options={[
    { value: 'created', label: 'Created', status: 'info' },
    { value: 'transit', label: 'In transit', status: 'success' },
    { value: 'failed', label: 'Failed', status: 'danger' }
  ]}
  value={status}
  onChange={setStatus}
  searchable
/>
```

## Surfaces and dialogs

`SurfaceCard` is the single card implementation. `Card` is an alias for the base
role, so existing feature pages keep working while new pages can opt into the
richer roles.

| Role          | Use                                                                           |
| ------------- | ----------------------------------------------------------------------------- |
| `base`        | Standard content surface on the page canvas.                                  |
| `elevated`    | Surface that sits above the page, e.g. an inline panel or popover.            |
| `interactive` | Tappable card. Adds `cursor-pointer`, a brand hover border, and a focus ring. |
| `selected`    | The chosen card in a set, e.g. a courier or slot picker.                      |

| State      | Treatment                                             |
| ---------- | ----------------------------------------------------- |
| `default`  | Live `:hover`/`:focus-visible` behaviour.             |
| `hover`    | Border darkens to `border-secondary`.                 |
| `focused`  | 2px `outline-border-focus` ring with a matching halo. |
| `disabled` | `bg-disabled-bg` / `text-disabled-fg`, not clickable. |

`title`, `header`, and `actions` build the header; `children` render in the body
using the persona's surface padding; `footer` is pinned below a hairline. Leave
`title`/`header`/`actions` empty for a plain surface.

```tsx
<SurfaceCard
  variant="interactive"
  title="Order SWD-4821"
  header={<span className="text-xs text-secondary">Updated 2 min ago</span>}
  actions={<Button variant="ghost">Open</Button>}
  footer={<Button>Assign courier</Button>}
  onClick={openOrder}
>
  <RouteSummary order={order} />
</SurfaceCard>
```

`SurfaceCard` becomes focusable (`role="button"`, Enter/Space) only when `onClick`
is provided. Do not nest buttons or links inside a clickable card; move those
actions into `actions`/`footer` and keep the card non-clickable instead.

`ModalDrawerShell` owns Escape-to-close, backdrop dismissal, initial focus,
background scroll locking, and the pinned footer. `Modal` and `Drawer` are
pre-configured wrappers, so features should use those unless they need to switch
layout at runtime. The backdrop uses the theme-aware `bg-scrim` (modal) and
`bg-scrim-soft` (drawer) tokens, which carry their own alpha so no opacity
modifier is needed.

| Size           | Modal      | Drawer    |
| -------------- | ---------- | --------- |
| `sm`           | 560 x 380  | 480 x 520 |
| `md` (default) | 800 x 480  | 700 x 720 |
| `lg`           | 1040 x 640 | 960 x 880 |

```tsx
<Modal
  open={open}
  onClose={close}
  title="Manual dispatch override"
  description="Choose a courier"
  footer={<Button onClick={confirm}>Confirm</Button>}
>
  <CourierPicker />
</Modal>
```

## Status and transitions

`FsmStatusBadge` is the only place parcel-status color is decided. It reads
`publicStatusColors` (backed by the `--color-fsm-*` tokens) for every
`PublicOrderStatus` (Created, Pickup Assigned, Picked Up, In Transit, Out for
Delivery, Failed, Delivered, Cancelled, Returned to Sender), so no view invents
its own palette. The older `ASSIGNED` delivery state is accepted as an alias of
`PICKUP_ASSIGNED`. Figma has no Pickup Assigned badge yet; its blue
(`#1E40AF` / `#EFF6FF` / `#BFDBFE`) is a proposal to add to Figma.

| Size | Figma | Default for                 | Treatment                                        |
| ---- | ----- | --------------------------- | ------------------------------------------------ |
| `sm` | S     | `compact`                   | 10px label, 5px dot, `px-1.5 py-0.5`, 4px gap    |
| `md` | M     | `comfortable` and `kinetic` | 11px label, 6px dot, `px-2.5 py-1`, 6px gap      |
| `lg` | -     | alias of `md`               | kept so existing `size="lg"` calls still compile |

```tsx
<FsmStatusBadge state="IN_TRANSIT" />
<FsmStatusBadge state="RETURNED_TO_SENDER" size="sm" />
<FsmStatusBadge state="DELIVERED" sync />   {/* dashed, "DELIVERED · SYNCING" */}
<FsmStatusBadge state="DELIVERED" tone="muted" />
```

`tone="muted"` keeps the state vocabulary but drops the FSM palette, for steps
that have not happened yet. `DeliveryProgressStepper` uses it for upcoming
steps: completed and current steps render solid, future steps render muted, and
the connectors between reached steps turn `bg-status-success`. A `FAILED` or
`CANCELLED` current state appends the terminal badge after a fully muted track.

```tsx
<DeliveryProgressStepper current="IN_TRANSIT" />
```

`SlideToTransitionBar` turns a destructive-or-important courier transition into a
deliberate gesture. The track height comes from
`densityRecipes[...].slideTrack` (64px touch, 48px comfortable, 40px compact) and
the thumb is inset 4px, so touch density yields the 64px track / 56px thumb pair.

| Progress            | Behaviour                                                |
| ------------------- | -------------------------------------------------------- |
| `0`                 | Idle: label at rest.                                     |
| `>= 50%`            | Armed preview: brand border and the `confirmLabel` copy. |
| `>= 85%` on release | Commit: fires `onConfirm()`.                             |
| `< 85%` on release  | Snaps back without firing.                               |

Keyboard users can focus the bar and press `Enter` or `Space` to commit
immediately. `state` forces one visual state for catalog screens and makes the
bar non-interactive, so never pass it in product views.

```tsx
const { next, transition } = useFsmTransition();

<SlideToTransitionBar
  label={`Slide to ${next[0].replace('_', ' ').toLowerCase()}`}
  confirmLabel="Release to confirm"
  onConfirm={() => transition(next[0])}
/>;
```

## Feedback, actions, and data

`StatusBanner` is the one place tone color is decided. Each tone maps to a
`status-<tone>-bg` / `-fg` / `-border` triad, so banners work in both themes
without opacity modifiers on hex variables.

| Tone      | Role     | Use                                                       |
| --------- | -------- | --------------------------------------------------------- |
| `success` | `status` | Confirmed actions and settled states.                     |
| `info`    | `status` | Neutral guidance and context.                             |
| `warning` | `status` | Degraded but recoverable conditions, e.g. an SLA at risk. |
| `danger`  | `alert`  | Failed actions and blocking errors.                       |

```tsx
<StatusBanner tone="warning" title="SLA at risk" action={<button type="button">Reassign</button>}>
  Zone 4 has three parcels past their promised window.
</StatusBanner>
```

`ErgonomicActionBar` renders only the supplied allowed transitions, labels them
by the state they produce, and caps how many appear at once so couriers never
face a wall of choices. Pair it with `SlideToTransitionBar` when an action needs
deliberate confirmation.

```tsx
const { next, transition } = useFsmTransition();

<ErgonomicActionBar next={next} onTransition={transition} pendingState={pending} sticky />;
```

`HighDensityDataGrid` filters across every column, keeps the row count visible,
and uses `tabular-nums` so IDs, coordinates, and prices stay aligned. Numeric
columns should set `align: 'right'`.

```tsx
<HighDensityDataGrid
  rows={events}
  columns={[
    { key: 'id', label: 'Event ID' },
    {
      key: 'status',
      label: 'Status',
      render: (row) => <FsmStatusBadge state={row.deliveryState} />
    },
    { key: 'attempts', label: 'Attempts', align: 'right' }
  ]}
/>
```

`AiAssistantDock` keeps conversation state locally and attributes each answer to
`cited operational data`. Return a string from `onSubmit` to append an assistant
reply; without it the dock stays a prompt surface.

## Catalog and shell

`/catalog` renders every family on the light token set, grouped like the Figma
component library. Forced states (`state` props) exist for this page only.

Shell behaviour lives in `src/app/AppShell.tsx`, built from the shell parts in
`src/design-system/components/Shell/`:

- `AppShell` maps the signed-in persona to a Figma shell role (`customer`,
  `merchant`, `courier`, `dispatcher`, `system-admin`). `ADMIN_OPS` is a
  dispatcher unless the backend role is `SYSTEM_ADMIN`.
- Desktop: `SideNav` (256px, 72px icon rail below 1280px) plus a 56px `TopBar`.
  Customer and courier get a `BottomNav` tab bar on mobile (below `md`) instead
  of the side nav.
- Menu entries come from `routedNav` in `AppShell.tsx`: only pages that have a
  route today. The full Figma menus live in `roleShellSpec` and are shown in the
  catalog; add an entry to `routedNav` when its page and route exist.
- The dev persona bar sits above the top bar and switches the zustand session,
  which re-derives density and then navigates to the persona home.
- Sign-out lives in the side nav account footer. It clears the session and
  replaces the route with `/login`.
- `PublicHeader` is the two-row header for public pages (no login). It is not
  used by `AppShell`.

Dialogs and drawers accept `inline` for catalogs and docs: the panel renders in
normal flow without the scrim, scroll lock, Escape handling, or `aria-modal`.
Never pass `inline` in product views.

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
