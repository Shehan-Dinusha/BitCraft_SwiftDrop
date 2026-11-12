# Pricing Estimate API Contract

Owner: Sandali - Member 4 (pricing-zone-service)
Consumers: Dulanga - Member 3 (customer-order-service, booking wizard), Buddhika - Member 10 (AI tool `calculate_estimate`)
Status: frozen once Members 3 and 10 confirm. Changes after that need a message to both consumers first.

## Endpoint

| Item | Value |
|---|---|
| Method and path | `POST /api/v1/pricing/estimate` |
| Via gateway | `http://localhost:8080/api/v1/pricing/estimate` |
| Service (direct) | `http://localhost:8083/api/v1/pricing/estimate` |
| Auth | Open to any logged-in user (no admin role needed) |
| Content type | `application/json` |
| Alias kept for compatibility | `POST /api/v1/pricing/quote` (same engine, returns the full quote shape) |

The response is wrapped in the standard `ApiResponse<T>` envelope from `common-contracts`. The JSON shown below is the `T` inside that envelope (the `data` payload).

## Request

```json
{
  "pickupDistrict": "COLOMBO",
  "deliveryDistrict": "KANDY",
  "actualWeightKg": 2.50,
  "lengthCm": 20.0,
  "widthCm": 15.0,
  "heightCm": 10.0,
  "speed": "EXPRESS"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `pickupDistrict` | `SriLankanDistrict` enum | yes | One of the 25 districts, for example `COLOMBO` |
| `deliveryDistrict` | `SriLankanDistrict` enum | yes | One of the 25 districts |
| `actualWeightKg` | number | yes | Minimum `0.01` |
| `lengthCm` | number | no | Minimum `0.1` when sent |
| `widthCm` | number | no | Minimum `0.1` when sent |
| `heightCm` | number | no | Minimum `0.1` when sent |
| `speed` | `OrderSpeed` enum | no | `STANDARD`, `EXPRESS` or `SAME_DAY`. Defaults to `STANDARD` when omitted |

Dimensions are optional. Volumetric weight is only calculated when length, width and height are all sent and all greater than zero. Otherwise only the actual weight is used.

## Response

```json
{
  "pickupDistrict": "COLOMBO",
  "deliveryDistrict": "KANDY",
  "pickupZone": "ZONE_WESTERN",
  "deliveryZone": "ZONE_CENTRAL",
  "intraDistrict": false,
  "actualWeightKg": 2.50,
  "volumetricWeightKg": 0.60,
  "chargeableWeightKg": 2.50,
  "speed": "EXPRESS",
  "baseFee": 550.00,
  "weightFee": 240.00,
  "speedSurcharge": 395.00,
  "estimatedFee": 1185.00,
  "currency": "LKR",
  "pricingRuleVersionId": "PRICING-2026-V1",
  "strategy": "EXPRESS"
}
```

| Field | Type | Meaning |
|---|---|---|
| `pickupDistrict`, `deliveryDistrict` | enum | Echo of the request |
| `pickupZone`, `deliveryZone` | string | Resolved pricing zone codes, for example `ZONE_WESTERN` |
| `intraDistrict` | boolean | `true` when pickup and delivery district are the same |
| `actualWeightKg` | number | Weight sent in the request, 2 decimal places |
| `volumetricWeightKg` | number | Length x width x height / 5000, `0.00` when dimensions are not sent |
| `chargeableWeightKg` | number | The higher of actual and volumetric weight |
| `speed` | enum | The speed that was priced |
| `baseFee` | number | Starting fee in LKR (includes the first kilogram) |
| `weightFee` | number | Fee for each extra kilogram beyond the first, in LKR |
| `speedSurcharge` | number | Extra amount added by the speed multiplier, in LKR |
| `estimatedFee` | number | Total delivery fee in LKR: (`baseFee` + `weightFee`) x speed multiplier |
| `currency` | string | Always `LKR` |
| `pricingRuleVersionId` | string | Version of the pricing rules used. Orders store this value so an existing order keeps its old price when rules change |
| `strategy` | string | Pricing strategy that was used: `STANDARD`, `EXPRESS` or `SAME_DAY` (same value as `speed`) |

## How the fee is calculated

1. `chargeableWeightKg` = max(actual weight, volumetric weight), rounded to 2 decimal places.
2. Same district: base fee and per-kg fee come from the pickup zone. Different districts: the inter-district base fee and per-kg fee apply.
3. `weightFee` = (chargeable weight - 1 kg, rounded up to a whole kg) x per-kg fee, and `0.00` when the chargeable weight is 1 kg or less.
4. The speed strategy applies its multiplier to (`baseFee` + `weightFee`): `STANDARD` x1.00, `EXPRESS` and `SAME_DAY` use the multipliers configured on the pickup zone.
5. All money values are rounded to 2 decimal places (HALF_UP).

The actual rates are configuration and may change between rule versions. Always read the amounts from the response and never copy a rate into another service. The order service should store `estimatedFee` and `pricingRuleVersionId` from this response.

## Worked example

Colombo to Kandy, 2.50 kg, 20 x 15 x 10 cm, `EXPRESS`:

- Volumetric weight: 20 x 15 x 10 / 5000 = 0.60 kg, so chargeable weight = 2.50 kg
- Different districts: base fee 550.00
- Extra weight: (2.50 - 1) rounded up = 2 kg x 120.00 = 240.00
- Subtotal: 790.00
- Express x1.50 = 1185.00, so `speedSurcharge` = 395.00

## Errors

All errors use `ApiErrorResponse` from `common-contracts`. It always carries `status`, `error`, `message` and `path`. The `fieldErrors` map (field name to a list of messages) is only filled for validation errors.

| HTTP status | `message` | When |
|---|---|---|
| `400` | `Validation failed` | A required field is missing or a value is below its minimum (district, weight below 0.01, dimension below 0.1). `fieldErrors` lists the failing fields |
| `400` | `Malformed or invalid request body` | Malformed JSON, or an unknown enum value (for example a district or speed name that does not exist) |
| `400` | `No pricing strategy is available for speed: <SPEED>` | The requested speed has no pricing strategy |

Validation error example (missing weight):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/pricing/estimate",
  "fieldErrors": {
    "actualWeightKg": ["Actual weight is mandatory"]
  }
}
```

Field messages used by this endpoint:

| Field | Message |
|---|---|
| `pickupDistrict` | `Pickup district is mandatory` |
| `deliveryDistrict` | `Delivery district is mandatory` |
| `actualWeightKg` | `Actual weight is mandatory` or `Actual weight must be at least 0.01 kg` |
| `lengthCm` | `Length must be at least 0.1 cm` |
| `widthCm` | `Width must be at least 0.1 cm` |
| `heightCm` | `Height must be at least 0.1 cm` |

Consumers should rely on the HTTP status and the field names. The message texts may be reworded, so do not match on them.

## Notes for consumers

- Member 3: call this endpoint instead of keeping a local copy of the pricing calculation. Send the districts as the enum names shown above.
- Member 10: the AI tool can map a city name to its district enum before calling this endpoint.
- Location input is district based. Postal codes and city fields are not part of this contract.