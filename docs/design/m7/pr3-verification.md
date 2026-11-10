# PR 3: merchant B2B order API

## Scope

Member 7 adds single and bulk B2B submission, phone/district/weight normalization, order-service forwarding, and persistent retry receipts. Merchant ingestion no longer generates tracking numbers or publishes `order.created`; Member 3 owns order persistence, tracking numbers, pricing and event publication.

No gateway, IAM, pricing, dispatch or order-service source is changed. The only shared deployment change is the merchant container's `ORDER_SERVICE_URL`. The merchant API specification and its generated TypeScript file are synchronized.

## Run

With Docker Desktop running, from the repository root:

```powershell
docker compose --profile apps up -d --build merchant-b2b-service customer-order-service pricing-zone-service
docker compose ps
docker compose logs --tail 80 merchant-b2b-service
```

Member 7 health: `http://localhost:8086/actuator/health`.
Swagger: `http://localhost:8086/swagger-ui.html`, tag **B2bOrders**.

Flyway V4 creates `merchant_order_receipts` in the merchant database only. New local configuration uses `ORDER_SERVICE_URL=http://localhost:8082`; Compose and Kubernetes override this with the appropriate internal hostname.

Use an existing **APPROVED** merchant. Pending/rejected/suspended merchants receive 403. This PR adds no approval endpoints; the team must integrate PR 1's workflow separately if it is not already present.

## Request example

```json
{
  "externalRef": "SHOP-2026-001",
  "pickup": {
    "addressLine1": "10 Galle Road",
    "city": "Colombo",
    "district": "colombo",
    "postalCode": "00300",
    "contactName": "Test Shop",
    "contactPhone": "077 123 4567"
  },
  "delivery": {
    "addressLine1": "25 Kandy Road",
    "city": "Gampaha",
    "district": "GAMPAHA",
    "postalCode": "11000",
    "contactName": "Test Recipient",
    "contactPhone": "+94 71 123 4567"
  },
  "parcel": {
    "weightGrams": 1500,
    "prohibitedItemsDeclared": true,
    "itemDescription": "Books"
  },
  "speed": "express"
}
```

Supply exactly one of `actualWeightKg` and `weightGrams`. Weight limits are 0.01–50 kg. Normalization produces E.164 Sri Lankan numbers, district enums, and kilograms. Invalid districts are never replaced with Colombo.

For **local direct-service testing only**, supply `X-Merchant-Id` and `X-User-Role: MERCHANT` to emulate authenticated gateway context. These headers are trusted only on the internal network; they are not a public authentication mechanism. The request body cannot select merchant identity.

| Check | Expected |
| --- | --- |
| POST `/api/v1/b2b/orders` with approved context | 201, Member 3's order ID/tracking number |
| Repeat same merchant/reference | 200, same IDs, `created=false` |
| Restart merchant service and repeat | Same persisted receipt, no downstream call |
| Concurrent identical requests | One local submission; same returned IDs |
| No trusted context, even with an arbitrary raw API key | 401 |
| Wrong role or unapproved merchant | 403 |
| Invalid phone/district/weight/declaration | 400 |
| Order service unavailable | 503; retry with the same reference |
| POST `/api/v1/b2b/orders/bulk` with `{"orders":[...]}` | Independent results in input order |
| More than 500 items or empty bulk | 400 |

Bulk items are validated independently: errors do not erase successfully committed orders. Successful local receipts survive restarts. If the remote order succeeds but local persistence fails, Member 3's own `(merchantId, externalRef)` idempotency protects a retry. A row lock serializes submissions for a merchant across replicas; this favors correctness over parallel submission throughput for one merchant.

## Existing JSON/CSV ingestion

The existing ingestion paths remain, but rows must now contain the details Member 3 actually requires. Incomplete rows return errors and receive no fabricated tracking number. The new optional JSON fields are `externalRef`, `deliveryCity`, `deliveryPostalCode`, `pickupCity`, `pickupPostalCode`, and `prohibitedItemsDeclared`.

CSV column order:

```csv
recipient_name,recipient_phone,delivery_district,delivery_address,package_weight_kg,delivery_speed,external_ref,delivery_city,delivery_postal_code,pickup_city,pickup_postal_code,prohibited_items_declared
Test Recipient,0771234567,COLOMBO,10 Galle Road,1.5,STANDARD,CSV-001,Colombo,00300,Gampaha,11000,true
```

The merchant profile supplies pickup street address, district and contact. Invalid/missing row details are recorded as failures. Correlation IDs are preserved through both the new APIs and existing ingestion paths. Legacy ingestion also caps batches at 500.

## Member 1 handoff (required before public gateway verification)

Add `/api/v1/b2b/orders` and `/api/v1/b2b/**` to the merchant route and API-key filter paths. Ensure API-key requests reach that filter rather than failing JWT authentication first; strip caller-supplied identity headers and inject verified merchant/role headers. Never expose `/internal/v1/orders` or key validation publicly.

Then test valid/invalid/missing API keys through port 8080 and confirm caller headers cannot spoof merchant identity. The merchant-only tests verify the trusted-context boundary; they do not claim that the current gateway already routes/authenticates these paths.

## Final integration evidence

1. Submit an approved merchant order and verify it exists in Member 3's database and tracking API.
2. Capture `order.created` from the order service and confirm it reaches dispatch.
3. Repeat single/bulk references and confirm no duplicate dispatch events.
4. Test the same flow through gateway API-key authentication after Member 1's changes.

Automated checks: `mvn -f backend/pom.xml -pl merchant-b2b-service -am verify`, frontend `npm run lint` and `npm run build`.
