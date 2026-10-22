# Velocity Design System — Ergonomic Guidelines

> SwiftDrop's internal design system for building consistent, accessible, and performant logistics UIs.

## 1. Design Principles

### 1.1 Clarity Over Cleverness
Logistics operators work under time pressure. Every interaction must be immediately understandable. Avoid decorative elements that don't serve a functional purpose.

### 1.2 Information Density Without Overwhelm
Dashboards must surface high-density data (live tracking, order queues, dispatch maps) while maintaining clear visual hierarchy through spacing, typography weight, and color coding.

### 1.3 Action-Oriented Interfaces
Primary actions (Assign Driver, Confirm Pickup, Escalate) must be within one click of the current context. Use floating action bars and contextual toolbars.

## 2. Color System

### 2.1 Semantic Palette

| Token                     | Hex       | Usage                                    |
|---------------------------|-----------|------------------------------------------|
| `--color-primary`         | `#2563EB` | Primary actions, active states           |
| `--color-primary-hover`   | `#1D4ED8` | Button hover, focus rings                |
| `--color-success`         | `#16A34A` | Delivered, confirmed, healthy            |
| `--color-warning`         | `#F59E0B` | Delayed, pending review                  |
| `--color-danger`          | `#DC2626` | Failed delivery, SLA breach              |
| `--color-info`            | `#0EA5E9` | Informational badges, tooltips           |
| `--color-neutral-50`      | `#F8FAFC` | Page background (light mode)             |
| `--color-neutral-900`     | `#0F172A` | Page background (dark mode)              |

### 2.2 Status Color Mapping

| Delivery Status      | Color Token        | Badge Style          |
|----------------------|--------------------|----------------------|
| Pending              | `--color-info`     | Outline              |
| In Transit           | `--color-primary`  | Filled               |
| Out for Delivery     | `--color-warning`  | Filled + Pulse anim  |
| Delivered            | `--color-success`  | Filled               |
| Failed               | `--color-danger`   | Filled + Icon        |
| Returned             | `--color-neutral`  | Outline              |

## 3. Typography

| Scale       | Size   | Weight | Line Height | Usage                      |
|-------------|--------|--------|-------------|----------------------------|
| Display     | 36px   | 700    | 1.2         | Dashboard KPI headlines    |
| Heading 1   | 24px   | 600    | 1.3         | Page titles                |
| Heading 2   | 20px   | 600    | 1.35        | Section headers            |
| Body        | 14px   | 400    | 1.5         | Default text               |
| Caption     | 12px   | 400    | 1.4         | Timestamps, metadata       |
| Monospace   | 13px   | 400    | 1.5         | Tracking IDs, API keys     |

**Font Stack:** `Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`

## 4. Spacing & Layout

- **Base unit:** `4px`
- **Grid:** 12-column responsive grid with `16px` gutters
- **Content max-width:** `1440px`
- **Sidebar width:** `260px` (collapsible to `64px`)
- **Card padding:** `16px` (compact) / `24px` (standard)

## 5. Component Standards

### 5.1 Data Tables
- Fixed header on scroll
- Sortable columns with aria-sort attributes
- Row selection with bulk action toolbar
- Pagination: 25 / 50 / 100 rows per page
- Empty state illustration + CTA

### 5.2 Maps
- Use Mapbox GL JS for delivery tracking
- Cluster markers when zoom < 12
- Real-time driver position updates via WebSocket (200ms throttle)
- Geofence polygons with semi-transparent fill

### 5.3 Notifications
- Toast: auto-dismiss after 5s, max 3 stacked
- Banner: persistent, dismissible, full-width
- Badge: numeric count on sidebar nav items

## 6. Accessibility

- WCAG 2.1 AA compliance minimum
- All interactive elements must have visible focus indicators (2px solid ring)
- Color is never the sole indicator of state — always pair with icons or text
- Minimum touch target: 44×44px
- Screen reader announcements for live data updates via `aria-live="polite"`

## 7. Motion & Animation

| Animation          | Duration | Easing                  | Usage                    |
|--------------------|----------|-------------------------|--------------------------|
| Page transition    | 200ms    | `ease-out`              | Route changes            |
| Modal enter        | 250ms    | `cubic-bezier(0.4,0,0.2,1)` | Dialog open         |
| Modal exit         | 200ms    | `cubic-bezier(0.4,0,1,1)`   | Dialog close        |
| Toast slide-in     | 300ms    | `ease-out`              | Notification appear      |
| Skeleton pulse     | 1.5s     | `ease-in-out` (loop)    | Loading states           |
| Map marker bounce  | 400ms    | `cubic-bezier(0.34,1.56,0.64,1)` | New delivery pin |

## 8. Dark Mode

- Default to system preference (`prefers-color-scheme`)
- Manual toggle persisted in `localStorage`
- All color tokens must have dark mode variants
- Avoid pure white (`#FFF`) on dark backgrounds — use `--color-neutral-100` (`#F1F5F9`)
