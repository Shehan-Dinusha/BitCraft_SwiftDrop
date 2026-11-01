# M3 handoff requests

Things Member 3 (customer-order-service) needs other members to know or do. Each section is added with the stage that creates it.

## Member 1 (api-gateway): internal tracking lookup is ready

Added in: PR 3, Stage 9.

The order service can now return a public-safe tracking view, so the tracking hub can show an order after a gateway restart.

- **Endpoint:** `GET http://customer-order-service:8082/internal/v1/orders/tracking/{trackingNumber}` (port 8082 when run locally).
- **Not routed through the gateway.** `GatewayRoutesConfig` only routes `/api/v1/orders/**` to this service and has no `/internal/**` route, so the endpoint is not reachable from outside. Please do not add one. The gateway should call it directly, the same way it already calls the merchant service's `/internal/v1/api-keys/validate`.
- **No user context.** There is no `X-User-Id`; the call is service-to-service and trusted-network-only. No auth scheme was invented. Open question for Member 1 and Member 7: do we want a shared internal secret header later? Until then the endpoint relies on network isolation (no gateway route, no public port in Kubernetes).
- **Tracking number format:** `SD-XXXX-XXXX` (letters and digits). A malformed number returns `400`, an unknown number returns `404`.

Sample response (`200`):

```json
{
  "success": true,
  "message": "Tracking retrieved successfully",
  "data": {
    "trackingNumber": "SD-7K4M-9QX2",
    "status": "CANCELLED",
    "history": [
      { "status": "CREATED",   "occurredAt": "2026-11-01T13:53:32.805359Z" },
      { "status": "CANCELLED", "occurredAt": "2026-11-01T13:53:34.399967Z" }
    ],
    "pickupCity": "Colombo 03",
    "pickupDistrict": "COLOMBO",
    "deliveryCity": "Kandy",
    "deliveryDistrict": "KANDY"
  },
  "timestamp": "2026-11-01T13:53:34.870Z"
}
```

What is deliberately **not** in the response: street addresses, names, phone numbers, email, customer id, merchant id, fees, and the internal dispatcher sub-status. History is ordered oldest first.

Error shape (`404` / `400`):

```json
{ "status": 404, "error": "Not Found", "message": "Order not found", "path": "/internal/v1/orders/tracking/{trackingNumber}", "timestamp": "..." }
```

Note for everyone: `GET /api/v1/orders/tracking/{trackingNumber}` (the public, gateway-routed one) returns the full order, so it now enforces ownership: only the order's customer, a dispatcher or a system admin can read it; anyone else gets `404`. Use the internal endpoint above for public tracking data.

## Member 7 (merchant-b2b-service): create merchant orders through the order service

Added in: PR 3, Stage 10.

The order service is meant to be the **only publisher of `order.created`**. Today `BulkOrderIngestionService` (via `MerchantEventPublisher`) publishes it itself, so merchant parcels never exist in the order database (no tracking lookup, no label, no history). Please call the endpoint below instead, and **remove your own `order.created` publish at the same time**. If both are active together, every merchant order produces two events.

- **Endpoint:** `POST http://customer-order-service:8082/internal/v1/orders` (internal only, not routed through the gateway, no `X-User-Id`). Optional header `X-Correlation-Id` (UUID) is carried into the event.
- **Idempotent on `(merchantId, externalRef)`:** the first call returns **201** with the new order and publishes `order.created` once; calling again with the same pair returns **200** with the same order and publishes nothing. Concurrent identical calls are safe (one 201, the rest 200, one event).
- **Price:** always calculated by the order service through the pricing client. Do not send or hard-code a price.
- **Identity convention:** the order is stored with `source = MERCHANT` and `customerId = merchantId` (the convention your service already uses). To read these orders through the public endpoints, the caller's `X-User-Id` must equal the merchant id; anyone else gets 404. Please confirm with Member 2 that a merchant's `X-User-Id` is the merchant id.

Request body (field names and rules; all text fields use the same allow-lists and limits as the customer booking form):

| Field | Required | Rules |
|---|---|---|
| `merchantId` | yes | UUID |
| `externalRef` | yes | 1-128 chars, `A-Z a-z 0-9 . _ : / # -` only |
| `pickupAddressLine1` / `pickupAddressLine2` | line 1 yes | 5-200 chars (line 2 optional, up to 200) |
| `pickupCity`, `pickupDistrict`, `pickupPostalCode` | yes | city 2-100, district enum (e.g. `COLOMBO`), 5-digit postal code |
| `pickupContactName`, `pickupContactPhone` | yes | name 2-100, phone `0771234567` or `+94771234567` |
| `deliveryAddressLine1` / `deliveryAddressLine2` | line 1 yes | as pickup |
| `deliveryCity`, `deliveryDistrict`, `deliveryPostalCode` | yes | as pickup |
| `recipientName`, `recipientPhone` | yes | as above |
| `recipientEmail` | **no** | valid email if present, max 128 |
| `lengthCm`, `widthCm`, `heightCm` | no | each at least 0.1; default **10** when missing |
| `actualWeightKg` | yes | 0.01 to 50 |
| `speed` | no | `STANDARD` (default), `EXPRESS`, `SAME_DAY` |
| `itemDescription` | no | up to 512 chars, no `<` or `>` |
| `declaredValue`, `codAmount` | no | 0 or more |
| `prohibitedItemsDeclared` | yes | must be `true` |

Your current `BulkOrderIngestItem` does not carry several of these (pickup address and contact, delivery city and postal code, split address lines, dimensions), so the merchant service has to fill them, for example the pickup details from the merchant account.

Response: the same `CustomerOrderResponse` wrapped in `ApiResponse` that the customer endpoint returns (`data.orderId`, `data.trackingNumber`, `data.estimatedFee`, ...). Errors: `400` validation (field names only), `503` pricing service unavailable (nothing was saved or published, safe to retry), `502` pricing service rejected the request.

`order.created` for a merchant order has `customerId = merchantId`, `merchantId` set, districts only (no addresses, names or phones).
