# Member 7: Merchant B2B documentation

PR 5 / Stage 17 deliverables for `uditha/docs/merchant-diagrams-evidence`, targeting `dev`.

| Deliverable | Editable source | Shareable exports |
| --- | --- | --- |
| B2B API order ingestion | [Mermaid](b2b-order-ingestion.mmd) | [PNG](b2b-order-ingestion.png), [SVG](b2b-order-ingestion.svg) |
| Merchant verification | [Mermaid](merchant-verification-workflow.mmd) | [PNG](merchant-verification-workflow.png), [SVG](merchant-verification-workflow.svg) |
| Demo and acceptance checklist | [Demo guide](demo-guide.md) | Markdown |
| Recorded tests and coverage | [Evidence index](../../test-reports/m7/README.md) | Surefire summaries, archived JaCoCo HTML and coverage PNG |
| PR review and completion status | [PR 5 review](pr5-review.md) | Acceptance checklist and suggested PR description |

The Mermaid sources contain the detailed contract. The PNG/SVG exports are presentation summaries of the same flows, produced by [render-diagrams.ps1](render-diagrams.ps1). Both exports use the same drawing operations. Update the diagram text in that script when changing a presentation layout, and keep the Mermaid source aligned.

## B2B ownership and transaction boundaries

- Member 1 authenticates the API key at the gateway, removes untrusted identity headers and supplies verified merchant identity. Key validation may be cached in Redis; revocation is therefore not a promise of instantaneous gateway cache invalidation.
- Member 7 requires an approved merchant, normalizes order input, serializes submissions for a merchant, and maintains retry receipts in its own database.
- Member 3 owns order persistence, pricing, tracking numbers and `order.created` publication. Its `(merchantId, externalRef)` idempotency protects a retry if the order was committed remotely but the local receipt was not.
- Member 5 consumes the order event for dispatch. Dispatch consumption is an integration contract, not evidence that a live distributed test passed.
- The single-order endpoint returns 201 for a newly created order or 200 for an existing order. Bulk input is capped at 500 items and reports independent item results; existing CSV/JSON ingestion uses the same submission service.
- The legacy publisher method still exists in the merchant module, but the inspected ingestion/submission path does not call it. The diagrams describe the active path rather than asserting that every legacy method was deleted.

## Approval workflow

Registration takes business details and derives the owner from the authenticated gateway context. Applications start `PENDING_APPROVAL`. Only `SYSTEM_ADMIN` can approve or reject a pending application. Approval requires an owner and records its audit fields; rejection requires a reason. The approval event is published after the transaction commits. It is not an outbox: this diagram does not promise durable publication if the broker is unavailable after commit.

IAM consumes `merchant.approved` to link the owner and merchant. Sign in again after approval so the token/profile contains the merchant claim. `SUSPENDED` is a recognized blocked status, but the inspected approval controller does not implement a suspension transition; it is not drawn as an available admin action.

## Current integration dependencies

At PR 5 review, the gateway merchant route does not include `/api/v1/b2b/**` or `/api/v1/admin/merchants` and `/api/v1/admin/merchants/**`. Member 1 must add those routes and coordinate B2B API-key handling with JWT filtering. The diagrams mark this dependency. Do not bypass the gateway with browser requests or expose `/internal/**` publicly.

After installing locked dependencies with `npm ci --no-audit --no-fund`, frontend lint and production build passed on 13 November 2026. This resolves the earlier missing installed `recharts` error. Vite reports a non-blocking warning for a minified JavaScript chunk larger than 500 kB. The build required filesystem access outside the agent sandbox. Backend verification was also rerun for PR 5; see the evidence index for results. These automated checks do not establish gateway authentication, broker consumption, or the complete live demo.

Frontend checks used the local working tree, which already contained a React Router upgrade from `^6.28.0` to `^7.18.5` in the package files. Those changes were not made by the dependency installation or this documentation update. Recheck the exact dependency set selected for the final PR before merging.

## Reproduce exports

From the repository root on Windows with .NET System.Drawing available:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File docs/design/m7/render-diagrams.ps1
```

The script writes only Member 7 diagram PNG/SVG files. Mermaid sources can also be rendered independently with Mermaid CLI if installed; no repository dependency changes are required.

To refresh test/coverage evidence, follow [the evidence instructions](../../test-reports/m7/README.md). Existing reports are labeled snapshots; do not present them as a fresh run.
