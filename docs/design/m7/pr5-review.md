# Member 7 PR 5: diagrams and submission evidence

Target: `dev`. Branch: `uditha/docs/merchant-diagrams-evidence`.

This PR provides the assigned B2B ingestion sequence and merchant verification workflow as editable Mermaid sources and PNG/SVG exports. It includes a demo checklist, sanitized merchant test reports, JaCoCo HTML and coverage summaries, and scripts to reproduce the exports and capture evidence.

The diagrams explain API-key authentication, approval guards, normalization, database ownership, retry/idempotency boundaries, order-service publication and IAM identity linking. Gateway dependencies and after-commit publication limitations are explicitly marked.

## Validation

- Both PNG diagrams and the coverage summary were rendered and visually inspected.
- Backend verification and current coverage results are recorded in the [evidence index](../../test-reports/m7/README.md) and [manifest](../../test-reports/m7/snapshot/manifest.json).
- After `npm ci --no-audit --no-fund`, frontend lint and production build passed on 13 November 2026. The missing installed `recharts` error is resolved. Vite's chunk-size warning is non-blocking; the build required filesystem access outside the agent sandbox.
- Frontend validation used existing local package changes upgrading React Router to `^7.18.5`. This documentation update leaves those changes untouched; validate the final PR dependency set before merging.
- Docker is not installed in this environment. Live gateway, database and RabbitMQ flows were not tested.

## Acceptance and handoff

- [x] B2B ingestion sequence source and PNG/SVG exports.
- [x] Merchant verification workflow source and PNG/SVG exports.
- [x] Demo script with positive and negative acceptance checks.
- [x] Sanitized automated test reports and refreshed coverage archive.
- [x] Contribution summary and known integration dependencies documented.
- [ ] Capture a browser screenshot/PDF of the archived JaCoCo report for final submission.
- [ ] Member 1: route `/api/v1/b2b/**` and admin merchant paths; coordinate API-key and JWT filters.
- [x] Install locked frontend dependencies and pass lint/build on the local working tree.
- [ ] Run the live demo checklist, including broker consumption and IAM token refresh.
- [ ] Commit/push this documentation, open PR into `dev`, and share exports with the final design-document owner.

PR 5 adds documentation and evidence only. The unused legacy merchant `publishOrderCreated` method is a separate Stage 12 cleanup; the active ingestion path does not call it. OAuth client credentials remain optional because API keys are implemented. A suspension endpoint is not part of Stage 17.

## Suggested PR title

`docs(merchant): add B2B diagrams and refreshed verification evidence`

## Suggested PR body

Adds Member 7's B2B ingestion and merchant verification diagrams with editable sources and PNG/SVG exports. Includes a reproducible evidence capture script, sanitized test/coverage archive, demo acceptance checklist and contribution summary.

Backend verification passed with 69 merchant tests and 77.58% overall line coverage. Frontend lint and production build passed after installing locked dependencies; Vite reports a non-blocking chunk-size warning. Diagram exports were visually checked. Live integration awaits Member 1 gateway routes and a Docker-enabled environment. Frontend checks used existing local React Router package changes, so recheck the dependency set chosen for the final PR. This documentation update changes no application source or shared service configuration.
