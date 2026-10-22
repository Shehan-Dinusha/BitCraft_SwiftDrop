---
trigger: glob
globs:
  - "frontend/src/**/*.{ts,tsx}"
---

# Frontend Velocity ergonomics and contracts

Before editing a view, inspect its persona folder, `src/design-system`, `density.ts`, `colors.ts`, and the relevant generated OpenAPI declarations.

- `features/courier/` uses `touch` density, minimum 56px targets, and bottom thumb-zone `ErgonomicActionBar`.
- `features/customer/` uses `comfortable` density and progressive disclosure.
- `features/admin-ops/` and `features/merchant/` use `compact` density and scannable grids.
- Use `tabular-nums` for tracking IDs, coordinates, ETAs, and currency.
- Use `deliveryStateColors` for every `DeliveryState`; do not add ad hoc state colors.
- Route REST requests through gateway `:8080/api/v1/...`.
- Use gateway tracking SSE `/api/v1/tracking/{trackingId}/stream` or WebSocket `/ws/tracking`.
- Never hand-write a backend contract interface when OpenAPI generation can produce it.

After contract changes, run `npm run api:sync:specs` or `npm run api:sync:local`, then run `npm run lint` and `npm run build`.
