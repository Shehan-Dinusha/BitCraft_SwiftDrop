# Member 7 PR 4: Merchant frontend

Base: `dev`. Branch: `uditha/feat/merchant-frontend`.

## Implemented scope

| Guide stage        | Implementation                                                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 13: Registration   | `/merchant/register`: validated business application, pending confirmation and application reference. Owner identity is supplied by the gateway. |
| 14: Admin approval | `/ops/merchants`: SYSTEM_ADMIN-only application list, status filter, business search, approval confirmation and required rejection reason.       |
| 15: Orders table   | Merchant dashboard: paged receipt list for JSON, CSV and B2B API submissions, tracking links and public delivery status badges.                  |
| 16: Key controls   | Generate, regenerate, revoke, expiry/last-used information, one-time secret display, copyable cURL and Postman collection.                       |

The CSV download includes the metadata required by PR 3. Upload results distinguish accepted and failed rows; acceptance does not imply dispatch or delivery.

## Supporting API addition

`GET /api/v1/merchants/{merchantId}/orders?page=0&size=20` returns submitted receipts, newest first. It reads only the merchant database, requires the verified MERCHANT role and matching `X-Merchant-Id`, and caps page size at 100. No entity or migration was changed. Delivery status comes from Member 1's existing public tracking API; tracking failures display “Status unavailable.”

The existing order-service list endpoint scopes ordinary callers to their user ID, whereas merchant orders use merchant ID. The merchant receipt endpoint avoids changing Member 3's service or returning an incorrect customer list.

Approval/registration/regeneration contracts were added to the merchant OpenAPI specification to match existing controllers. Only the merchant TypeScript declarations were regenerated using the repository's `openapi-typescript` CLI.

## Validation and remaining integration checks

Historical PR 4 results below describe the original review. PR 5 follow-up on 13 November 2026: installing locked dependencies resolved the missing `recharts` error; frontend lint and production build now pass on the local working tree. See the [current evidence index](../../test-reports/m7/README.md) for dependency context and remaining live checks.

- Backend `mvn -f backend/pom.xml -pl merchant-b2b-service -am test` passed: 69 merchant tests, including two receipt-list authorization/pagination tests, with zero failures or errors. Shared-contract tests also passed.
- Frontend lint currently stops on a pre-existing missing installed dependency: `recharts`, imported by Member 10's analytics page. No analytics source or package manifest was changed.
- Dependency installation requiring elevated access was declined. Frontend build and live UI flows are not verified in this environment.
- Docker is unavailable and no gateway/merchant/database/broker listeners were found on the checked local ports. Backend test success is not live integration evidence.

Before merging, install the declared frontend dependencies and run `npm run lint` and `npm run build`. Start the gateway, merchant service, IAM, order service, pricing and infrastructure, then verify:

1. Register as MERCHANT; application is PENDING_APPROVAL. Verify keys/ingestion remain unavailable before approval.
2. As SYSTEM_ADMIN, filter/search applications, approve one and reject another with a reason. Dispatchers must not access the review screen or backend admin operations.
3. Sign in again after approval so the merchant claim is present. Generate/copy a key, regenerate it and confirm the old key fails, then revoke the replacement.
4. Copy cURL or import the Postman collection, replace sample details and create an order through gateway API-key authentication.
5. Confirm the order appears in the merchant table with the same tracking number. Refresh delivery state and follow the public tracking link.
6. Upload the CSV template with valid pickup details, inspect successes/failures, and confirm the order table refreshes. Test empty results, unavailable APIs and invalid input.
7. Try another merchant's receipt URL and verify 403. Test missing identity, pagination and a tracking failure.

## Member 1 handoff

The inspected gateway route does not include `/api/v1/admin/merchants` and `/api/v1/admin/merchants/**`; add them to the merchant-service route for the approval screen. The `/api/v1/b2b/**` route and API-key/JWT filter coordination from PR 3 also remain required before copied API examples work through the gateway. Existing `/api/v1/merchants/**` routing covers registration, key management and the new order list.

No gateway, IAM, order-service, shared routing/design-system, or Member 8 webhook source files were edited. PR publication remains with the repository owner.
