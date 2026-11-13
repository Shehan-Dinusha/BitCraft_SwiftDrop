# Member 7 PR 5: diagrams and submission evidence

Target: `dev`. Branch: `uditha/docs/merchant-diagrams-evidence`.

This PR provides the assigned B2B ingestion sequence and merchant verification workflow as editable Mermaid sources and PNG/SVG exports. It includes a demo checklist, sanitized merchant test reports, JaCoCo HTML and coverage summaries, and scripts to reproduce the exports and capture evidence.

The diagrams explain API-key authentication, approval guards, normalization, database ownership, retry/idempotency boundaries, order-service publication and IAM identity linking. Gateway dependencies and after-commit publication limitations are explicitly marked.

## Validation

- Both PNG diagrams and the coverage summary were rendered and visually inspected.
- Backend verification and current coverage results are recorded in the [evidence index](../../test-reports/m7/README.md) and [manifest](../../test-reports/m7/snapshot/manifest.json).
- Frontend lint and build were attempted on 13 November 2026. Both fail with TS2307 for the missing installed `recharts` dependency in `AnalyticsDashboardPage.tsx`.
- Docker is not installed in this environment. Live gateway, database and RabbitMQ flows were not tested.

## Acceptance and handoff

- [x] B2B ingestion sequence source and PNG/SVG exports.
- [x] Merchant verification workflow source and PNG/SVG exports.
- [x] Demo script with positive and negative acceptance checks.
- [x] Sanitized automated test reports and refreshed coverage archive.
- [x] Contribution summary and known integration dependencies documented.
- [ ] Capture a browser screenshot/PDF of the archived JaCoCo report for final submission.
- [ ] Member 1: route `/api/v1/b2b/**` and admin merchant paths; coordinate API-key and JWT filters.
- [ ] Install declared frontend dependencies and pass lint/build.
- [ ] Run the live demo checklist, including broker consumption and IAM token refresh.
- [ ] Commit/push this documentation, open PR into `dev`, and share exports with the final design-document owner.

PR 5 adds documentation and evidence only. The unused legacy merchant `publishOrderCreated` method is a separate Stage 12 cleanup; the active ingestion path does not call it. OAuth client credentials remain optional because API keys are implemented. A suspension endpoint is not part of Stage 17.

## Suggested PR title

`docs(merchant): add B2B diagrams and refreshed verification evidence`

## Suggested PR body

Adds Member 7's B2B ingestion and merchant verification diagrams with editable sources and PNG/SVG exports. Includes a reproducible evidence capture script, sanitized test/coverage archive, demo acceptance checklist and contribution summary.

Backend verification results are linked in the evidence index. Diagram exports were visually checked. Frontend lint/build are blocked by missing installed `recharts`; live integration awaits Member 1 gateway routes and a Docker-enabled environment. No application source or shared service configuration is changed.
