---
trigger: glob
globs:
  - "frontend/src/**/*.{ts,tsx}"
---

# Frontend ergonomics and contracts

Before editing a view, inspect its persona folder, `src/design-system`, `density.ts`, `colors.ts`, and the relevant generated OpenAPI declarations.

- `features/courier/` uses `kinetic` density (`touch` is a deprecated alias), minimum 56px targets, and bottom thumb-zone `ErgonomicActionBar`.
- `features/customer/` uses `comfortable` density and progressive disclosure.
- `features/admin-ops/` and `features/merchant/` use `compact` density and scannable grids.
- Use `tabular-nums` for tracking IDs, ETAs, and currency.
- Use `deliveryStateColors` for every `DeliveryState`; do not add ad hoc state colors.
- Route REST requests through gateway `:8080/api/v1/...`.
- Use gateway tracking SSE `/api/v1/tracking/{trackingId}/stream` or WebSocket `/ws/tracking`.
- Never hand-write a backend contract interface when OpenAPI generation can produce it.
- The logo is the `BrandMark` component and the product name is "SwiftDrop" (never "Velocity").
- PWA: never cache API responses in the service worker; ask for browser permissions only from a user tap; keep PWA prompts pinned to the top. SwiftDrop does not use device location.

After contract changes, run `npm run api:sync:specs` or `npm run api:sync:local`, then run `npm run lint`, `npm test` and `npm run build`. See [frontend/AGENTS.md](../../frontend/AGENTS.md) for the branding and PWA rules.
