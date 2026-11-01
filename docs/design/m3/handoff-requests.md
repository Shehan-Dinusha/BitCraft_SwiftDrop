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
