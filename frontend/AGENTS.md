# Frontend and Velocity Design System Agent Instructions

These rules apply to all work below [frontend](.).

## Inspect first

Read [package.json](package.json), [tailwind.config.ts](tailwind.config.ts), the target persona feature folder, [frontend/src/design-system](src/design-system), the relevant API service, and any generated declaration under [src/types/generated](src/types/generated) before editing.

## Velocity density and ergonomics

The live density mapping is defined in [density.ts](src/design-system/tokens/density.ts):

| Persona folder        | Density       | Required interaction model                                                                    |
| --------------------- | ------------- | --------------------------------------------------------------------------------------------- |
| `features/courier/`   | `kinetic`     | Fitts-friendly controls, minimum 56px tap targets, and bottom thumb-zone `ErgonomicActionBar` |
| `features/customer/`  | `comfortable` | Hick’s Law progressive disclosure; reveal complexity progressively                            |
| `features/admin-ops/` | `compact`     | High-density scannable operational grids                                                      |
| `features/merchant/`  | `compact`     | High-density scannable business grids                                                         |

Use the primitives and components under [src/design-system](src/design-system), especially `Button`, `Card`, `FsmStatusBadge`, `HighDensityDataGrid`, and `ErgonomicActionBar`. The Tailwind theme exposes `min-h-target` and `min-w-target` as 56px target utilities.

Use `tabular-nums` for tracking IDs, coordinates, ETAs, prices, and currency. Use the semantic delivery-state mapping in [colors.ts](src/design-system/tokens/colors.ts) for every `DeliveryState`; do not invent per-view colors.

## API and real-time integration

- Route REST calls through the gateway at `:8080/api/v1/...`; do not call downstream service ports from browser code.
- Do not manually create TypeScript interfaces for backend REST contracts. Use `npm run api:sync:specs` for checked-in YAML or `npm run api:sync:local` for live gateway documents.
- Generated files under `src/types/generated/` are build outputs; regenerate them rather than hand-editing them.
- Public parcel tracking uses Member 1’s gateway hub: SSE at `/api/v1/tracking/{trackingId}/stream` or WebSocket at `/ws/tracking?trackingId={trackingId}`.
- Keep tracking payloads aligned with the sanitized `PublicTrackingViewDto`; never render courier phone numbers, exact home addresses, or internal merchant payout data.

## Validation

```powershell
cd frontend
npm run lint
npm run build
npm run api:sync:specs
```

When a live gateway is available, also run `npm run api:sync:local` and manually verify the affected persona flow.
