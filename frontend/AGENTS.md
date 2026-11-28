# Frontend and SwiftDrop Design System Agent Instructions

These rules apply to all work below [frontend](.).

## Inspect first

Read [package.json](package.json), [tailwind.config.ts](tailwind.config.ts), the target persona feature folder, [frontend/src/design-system](src/design-system), the relevant API service, and any generated declaration under [src/types/generated](src/types/generated) before editing.

## Density and ergonomics

The live density mapping is defined in [density.ts](src/design-system/tokens/density.ts):

| Persona folder        | Density       | Required interaction model                                                                    |
| --------------------- | ------------- | --------------------------------------------------------------------------------------------- |
| `features/courier/`   | `kinetic`     | Fitts-friendly controls, minimum 56px tap targets, and a bottom thumb-zone action: **one** `SlideToTransitionBar` per delivery step (`courierStep`), never a slide plus buttons for the same step |
| `features/customer/`  | `comfortable` | Hick’s Law progressive disclosure; reveal complexity progressively                            |
| `features/admin-ops/` | `compact`     | High-density scannable operational grids                                                      |
| `features/merchant/`  | `compact`     | High-density scannable business grids                                                         |

Use the primitives and components under [src/design-system](src/design-system), especially `Button`, `Card`, `FsmStatusBadge`, `HighDensityDataGrid`, and `ErgonomicActionBar`. The Tailwind theme exposes `min-h-target` and `min-w-target` as 56px target utilities.

Use only the design-token colours from [tailwind.config.ts](tailwind.config.ts) (`text-primary`, `bg-card`, `bg-canvas`, `border-border-subtle`, `brand-*`, `status-{success,warning,danger,info}-*`). Never use raw palette classes such as `text-slate-500`, `bg-rose-50` or `bg-white` in feature code: they ignore dark mode and drift from the Figma theme. The token colours are CSS variables, so opacity modifiers like `bg-status-danger/10` do not work; use the `-bg` token instead. The design-system catalog is the only exception.

Scrollbars are hidden everywhere in `src/index.css`. Use `overflow-x-auto` / `overflow-y-auto` for scrolling areas, so they get the edge fade that shows there is more; do not style scrollbars per component.

Keep copy short. Use a title and the controls; add text only when it changes what the user does next (an irreversible consequence, a real limit, an error with a next step). No page subtitles that restate the title, no hints that repeat the label, no explanations of system internals.

Placeholders never show an example answer (a name, phone, address, postal code, plate, "e.g. ..."): an example in the box looks already filled in. Put a format or example in the field's hint instead. Placeholders may only give an instruction, such as "Search orders", "Choose a district" or, on the sign-in screens whose labels are visually hidden, the field name.

Use `tabular-nums` for tracking IDs, ETAs, prices, and currency. Use the semantic delivery-state mapping in [colors.ts](src/design-system/tokens/colors.ts) for every `DeliveryState`; do not invent per-view colors.

## API and real-time integration

- Route REST calls through the gateway at `:8080/api/v1/...`; do not call downstream service ports from browser code.
- Do not manually create TypeScript interfaces for backend REST contracts. Use `npm run api:sync:specs` for checked-in YAML or `npm run api:sync:local` for live gateway documents.
- Generated files under `src/types/generated/` are build outputs; regenerate them rather than hand-editing them.
- Public parcel tracking uses Member 1’s gateway hub: SSE at `/api/v1/tracking/{trackingId}/stream` or WebSocket at `/ws/tracking?trackingId={trackingId}`.
- Keep tracking payloads aligned with the sanitized `PublicTrackingViewDto`; never render courier phone numbers, exact home addresses, or internal merchant payout data.
- Sessions: the access token lives in `sessionStorage` (per tab); the refresh token is the HttpOnly `swiftdrop_refresh` cookie, shared by all tabs and single use (a reused one revokes every session of the user). A tab without a token signs in from the cookie (`restoreSession`, run by `RoleGuard`). Every refresh goes through `refreshSession` in `apiClient.ts`, which holds the `swiftdrop-auth-refresh` Web Lock so two tabs never spend the same cookie; never call `/v1/auth/refresh` another way.

## Branding and public pages

- The product name is "SwiftDrop" in every visible string. The logo is the `BrandMark` component (`src/design-system/components/BrandMark`), used in the side nav, top bar, mobile header, auth screens, public header, landing page and catalog. Never use a generic icon tile (such as `Package`) as the logo; `Package` and similar icons are fine as parcel icons inside pages.
- Favicons, the iPhone touch icon and the PWA icons (including maskable ones with a safe zone) are in `public/`. The full-size transparent logo is `docs/brand/swiftdrop-logo.png`.
- Public routes (no sign-in): `/` landing page (`features/landing`), `/track` public tracking, the auth pages, and `/catalog`. The public header (`PublicHeader` in `Shell/TopBar.tsx`) shows only the brand, quick track and sign-in. Do not add links to pages that do not exist.

- Navigation menus are one flat list per role: no section headings ("Shipping", "Updates") and no two items with the same icon. The shell is documented in [COMPONENTS.md](COMPONENTS.md).
- The system admin oversees the platform and does not do the dispatcher's daily work. Their menu is Analytics (their home, `/ops/analytics`), User management, Cash, Working days and Zone pricing. The control tower, Dispatch, Couriers and Merchant applications pages are dispatcher-only (`RoleGuard dispatcherOnly` sends an admin to Analytics). Suspending or reactivating a merchant's shop is done from that merchant's row in User management. Working days edits the working days, hours, lunch window and cut-offs (`WorkingHoursCard`) as well as the holidays; screens read the hours from `GET /api/v1/dispatch/calendar/today` and never hard-code them. Use `homeFor()` from `src/app/roleHome.ts`, not `roleHome[...]`, wherever a signed-in user is sent home.

- The customer assistant button (`CustomerAiAssistant`) floats bottom-right and moves to the header when a row marked `data-assistant-avoid`, or the end of the page, is under it. Mark a row of page buttons that can reach that corner.
- There is no map anywhere in the product, so add no map placeholder.

## PWA rules

The app is an installable PWA built with `vite-plugin-pwa` (config in `vite.config.ts`, UI in `src/features/pwa/`).

- The service worker precaches the app's own build output only. **Never cache API responses**: they hold addresses, phone numbers and orders, and a shared phone must not show the previous user's data. `/api`, `/ws` and the gateway docs paths are on the navigation-fallback denylist.
- `registerType: 'prompt'`: a new version waits until the user taps "Reload", so a courier is never reloaded mid-delivery.
- `PwaLayer` (mounted in `main.tsx`) shows the offline banner, update prompt and install prompt, pinned to the **top** of the screen so they never cover the bottom navigation or the courier's step slide. The install prompt is for signed-in couriers and customers only.
- Never ask for a browser permission (notifications, camera) on page load; only from a user tap. Device location is not used at all.
- nginx serves `sw.js`, `manifest.webmanifest` and `index.html` with `Cache-Control: no-cache` through a `map`. The nginx config exists twice, `frontend/nginx.conf` (Docker) and `frontend.nginxConfig` in `infra/helm/swiftdrop/values.yaml` (Kubernetes); change both. In nginx, an `add_header` inside a `location` drops every server-level security header, so do not add one there.
- PWA features only run in a production build: `npm run build && npm run preview`, not `npm run dev`.

## Validation

```powershell
cd frontend
npm run lint            # type-check + ESLint
npm test                # Vitest
npm run build
npm run api:sync:specs  # after any contract change; commit the regenerated types
```

When a live gateway is available, also run `npm run api:sync:local` and manually verify the affected persona flow.

To check a page visually without a browser session, serve the production build and take headless Chrome screenshots:

```bash
npx vite preview --port 4179 --strictPort &
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --hide-scrollbars \
  --window-size=1366,2300 --screenshot=desktop.png http://localhost:4179/
```

Headless Chrome will not lay out narrower than about 500px, so use `--window-size=500,…` for a phone-width check; at 390px pages look clipped even when they are fine.
