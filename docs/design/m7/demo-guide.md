# Member 7 demo and review checklist

These steps are a rehearsal plan, not a record of a completed live demonstration.

## Prerequisites

1. Merge or coordinate the Member 1 admin merchant and B2B gateway routes described in [README](README.md).
2. Install declared frontend dependencies; run `npm.cmd run lint` and `npm.cmd run build` inside `frontend`.
3. Start the required IAM, merchant, gateway, order, pricing and dispatch services and their infrastructure using the team's local-development instructions. Merchant health is `http://localhost:8086/actuator/health`; gateway REST traffic is through `http://localhost:8080/api/v1/...`.
4. Use separate authenticated MERCHANT and SYSTEM_ADMIN browser sessions. Use synthetic business/contact data and keep API keys out of screenshots and recordings.

## Demonstration

| Step | Action | Acceptance evidence |
| --- | --- | --- |
| 1 | MERCHANT submits `/merchant/register` with business details. | Pending confirmation and merchant reference; database status `PENDING_APPROVAL`. |
| 2 | Attempt keys or order ingestion before approval. | Backend denies unapproved merchant operations; UI does not offer enabled ingestion/key controls. |
| 3 | SYSTEM_ADMIN opens `/ops/merchants`, filters pending applications and approves the business. | Approval succeeds with audit fields; `merchant.approved` is observable without displaying private payloads. |
| 4 | Reject a separate synthetic pending application with a reason. | Status `REJECTED`, reason displayed, key/order access remains denied. |
| 5 | MERCHANT signs in again and opens `/merchant`. | Refreshed IAM profile contains the linked merchant identity. |
| 6 | Generate an API key. Copy the one-time secret privately and close the dialog. | Listing shows a prefix and last-used/expiry information, not the raw key. |
| 7 | Copy the generated cURL or import the Postman collection; replace sample details and submit through the gateway. | HTTP 201 with Member 3's order ID and tracking number. |
| 8 | Repeat with the same merchant and external reference. | HTTP 200 and the same order/tracking number; no duplicate order or dispatch event. |
| 9 | Refresh the merchant orders table and open its tracking link. | API-created order appears; delivery status comes from public tracking. |
| 10 | Upload the updated CSV template using valid merchant pickup details. | Per-row acceptance/errors shown; accepted orders appear after refresh. |
| 11 | Regenerate the key; retry the old secret, then revoke the replacement. | Old/revoked secrets fail merchant validation. Gateway behavior must be checked after its configured API-key cache expires. |
| 12 | Hand over to Member 8 for webhook demonstration. | Webhook behavior remains Member 8's scope. |

## Negative cases

- Missing/invalid API key at the gateway returns 401.
- Forged merchant/role headers must be stripped at the edge.
- A non-admin cannot approve or reject; a merchant cannot read another merchant's receipt list.
- Invalid phone/district/weight is rejected; bulk input over 500 is rejected and valid bulk input reports item errors independently.
- Order-service unavailability returns an explicit error; retry uses the same external reference.
- A tracking failure shows `Status unavailable`, not a fabricated delivery status.

## Evidence to capture after rehearsal

Record the date, tested commit, service health, gateway paths, sanitized order reference/status and observed event routing. Check off the table only after observing each result. Do not capture raw keys, JWTs, real contact details or environment secrets.

The member guide suggests rehearsing twice from clean infrastructure. Resetting persistent volumes deletes data: arrange that separately on disposable demo infrastructure; this documentation PR does not execute a reset.

## Contribution summary for the team lead

- Backend: merchant approval, hashed API keys and rotation, B2B normalization/forwarding, retry receipts and merchant-scoped order listing.
- Frontend: business registration, admin review, merchant orders/tracking, API-key controls and CSV ingestion UI.
- Evidence: [tests/coverage](../../test-reports/m7/README.md), [B2B diagram](b2b-order-ingestion.png), [verification diagram](merchant-verification-workflow.png). Local merge history records [PR 1 #40](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/pull/40), [PR 2 #43](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/pull/43), [PR 3 #44](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/pull/44), and [PR 4 #57](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/pull/57). Add the PR 5 link after publication; these links were derived from local Git history, not a fresh remote query.
