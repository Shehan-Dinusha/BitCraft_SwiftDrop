# SwiftDrop Domain Event Payload Schemas & Specifications

> **Owner:** Member 9 (Notification & Pub/Sub Integration Specialist)  
> **Broker Exchange:** `swiftdrop.events.exchange` (Type: `topic`, Durable: `true`)  
> **DLX Exchange:** `swiftdrop.dlx.exchange` (Type: `topic`, Durable: `true`)  
> **Source of Truth:** [`backend/common-contracts/src/main/java/com/swiftdrop/contracts/events`](file:///d:/PROJECTS/BitCraft_SwiftDrop/backend/common-contracts/src/main/java/com/swiftdrop/contracts/events)

---

## 1. Overview & Event Bus Design Standards

All asynchronous inter-service domain communication in SwiftDrop flows over RabbitMQ via AMQP 0-9-1 using a unified topic exchange: `swiftdrop.events.exchange`.

### Global Envelope Conventions
Every event serialized onto the bus complies with the following baseline rules:
1. **Zero Plaintext PII:** Names, street addresses, national IDs, and unmasked phone numbers/emails **never** travel over the message bus. District codes (`SriLankanDistrict`) and UUIDs are used exclusively.
2. **Correlation ID Tracking:** Every event carries a `correlationId` (aliased to `traceId`) to ensure distributed request tracing across services.
3. **Idempotency Key:** Every event carries a globally unique `eventId` (UUIDv4) used by downstream consumers to detect duplicates.
4. **Resilient Deserialization:** Jackson configured with `@JsonIgnoreProperties(ignoreUnknown = true)` across all contract records to permit forward-compatible schema evolution.

---

## 2. Event Payload Specifications

---

### 2.1 `order.created` — `OrderCreatedEvent`

* **Producer:** `customer-order-service` (:8082)
* **Routing Key:** `order.created`
* **Destination Queues:** `dispatch.order-created.queue`, `notification.events.queue`, `webhook.events.queue`
* **Consumers:** `dispatch-assignment-service`, `notification-service`, `webhook-resiliency-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier for deduplication | Safe |
| `correlationId` | `UUID` | No | Distributed trace correlation identifier | Safe |
| `orderId` | `UUID` | No | Order primary key | Safe |
| `trackingNumber` | `String` | No | Human-readable tracking number (e.g. `SD-7K4M-9QX2`) | Safe |
| `customerId` | `UUID` | No | Ordering customer identity UUID | Safe |
| `merchantId` | `UUID` | Yes | Merchant UUID (if placed via B2B merchant portal) | Safe |
| `pickupDistrict` | `String` (Enum) | No | Sri Lankan district of origin (e.g. `COLOMBO`) | Safe (No Street) |
| `deliveryDistrict` | `String` (Enum) | No | Sri Lankan district of destination (e.g. `KANDY`) | Safe (No Street) |
| `speed` | `String` (Enum) | No | Delivery service level: `STANDARD` or `EXPRESS` | Safe |
| `codAmount` | `BigDecimal` | Yes | Cash on Delivery collection amount | Safe |
| `declaredValue` | `BigDecimal` | Yes | Insured declared value of package contents | Safe |
| `estimatedPrice` | `BigDecimal` | No | Calculated shipping charge | Safe |
| `currency` | `String` | No | ISO currency code (default: `LKR`) | Safe |
| `timestamp` | `Instant` (ISO 8601) | No | Order creation UTC timestamp | Safe |
| `lengthCm`, `widthCm`, `heightCm` | `BigDecimal` | Yes | Parcel size; absent in events published before it was added | Safe |
| `actualWeightKg` | `BigDecimal` | Yes | Parcel weight; absent in events published before it was added | Safe |

#### Example JSON Payload
```json
{
  "eventId": "7a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d",
  "correlationId": "f1e2d3c4-b5a6-9788-7654-3210fedcba98",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "trackingNumber": "SD-7K4M-9QX2",
  "customerId": "018f6c32-b7e1-7d34-9128-498c257b4201",
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "pickupDistrict": "COLOMBO",
  "deliveryDistrict": "KANDY",
  "speed": "EXPRESS",
  "codAmount": 2500.00,
  "declaredValue": 10000.00,
  "estimatedPrice": 650.00,
  "currency": "LKR",
  "timestamp": "2026-11-13T14:30:00Z",
  "lengthCm": 30,
  "widthCm": 20,
  "heightCm": 10,
  "actualWeightKg": 2.5
}
```

---

### 2.1a `order.updated` — `OrderUpdatedEvent`

* **Producer:** `customer-order-service` (:8082), when an order is edited before a courier is assigned and its districts, speed, size or weight changed
* **Routing Key:** `order.updated`
* **Destination Queues:** `dispatch.order-updated.queue`, `order.events.queue`
* **Consumers:** `dispatch-assignment-service` (size and weight always; districts and speed only while the order still waits for its first courier)

Fields: `eventId`, `correlationId`, `orderId`, `trackingNumber`, `pickupDistrict`, `deliveryDistrict`, `speed`, `lengthCm`, `widthCm`, `heightCm`, `actualWeightKg`, `timestamp`. The values are the whole order after the edit, not only what changed. No addresses or contacts.

---

### 2.2 `order.cancelled` — `OrderCancelledEvent`

* **Producer:** `customer-order-service` (:8082)
* **Routing Key:** `order.cancelled`
* **Destination Queues:** `dispatch.order-cancelled.queue`, `order.events.queue`
* **Consumers:** `dispatch-assignment-service`, `customer-order-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `orderId` | `UUID` | No | Cancelled order identifier | Safe |
| `customerId` | `UUID` | No | Ordering customer identifier | Safe |
| `reason` | `String` | Yes | Cancellation justification | Safe |
| `cancelledBy` | `String` | No | Actor initiating cancellation (`CUSTOMER`, `DISPATCHER`, `SYSTEM`) | Safe |
| `timestamp` | `Instant` | No | Cancellation timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "182f7c32-b7e1-7d34-9128-498c257b4202",
  "correlationId": "f1e2d3c4-b5a6-9788-7654-3210fedcba98",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerId": "018f6c32-b7e1-7d34-9128-498c257b4201",
  "reason": "Customer requested change of address",
  "cancelledBy": "CUSTOMER",
  "timestamp": "2026-11-13T14:45:00Z"
}
```

---

### 2.3 `parcel.status.changed.<status>` — `ParcelStatusChangedEvent`

* **Producer:** `delivery-execution-service` (:8085)
* **Routing Key:** `parcel.status.changed` (and hierarchical variants e.g. `parcel.status.changed.DELIVERED`)
* **Destination Queues:** `notification.events.queue`, `dispatch.parcel-status.queue`, `webhook.events.queue`, `swiftdrop.gateway.tracking.queue`, `analytics.events.queue`
* **Consumers:** `notification-service`, `dispatch-assignment-service`, `webhook-resiliency-service`, `api-gateway`, `analytics-ai-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Deduplication event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `trackingNumber` | `String` | No | Public parcel tracking number | Safe |
| `orderId` | `UUID` | No | Order identifier | Safe |
| `customerId` | `UUID` | Yes | Customer identity (for user notification matching) | Safe |
| `previousPublicStatus` | `String` (Enum) | Yes | Status before transition | Safe |
| `newPublicStatus` | `String` (Enum) | No | Current public status (e.g. `DELIVERED`, `OUT_FOR_DELIVERY`) | Safe |
| `internalSubStatus` | `String` (Enum) | Yes | Internal queue status | Safe |
| `district` | `String` (Enum) | Yes | Current checkpoint district | Safe |
| `destinationDistrict`| `String` (Enum) | Yes | Target delivery district | Safe |
| `failureReason` | `String` (Enum) | Yes | Failure classification (e.g. `CUSTOMER_UNREACHABLE`) | Safe |
| `timestamp` | `Instant` | No | State transition timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "trackingNumber": "SD-7K4M-9QX2",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerId": "018f6c32-b7e1-7d34-9128-498c257b4201",
  "previousPublicStatus": "OUT_FOR_DELIVERY",
  "newPublicStatus": "DELIVERED",
  "internalSubStatus": "DELIVERED",
  "district": "COLOMBO",
  "destinationDistrict": "COLOMBO",
  "failureReason": null,
  "timestamp": "2026-11-13T16:15:30Z"
}
```

---

### 2.4 `dispatch.courier.assigned` — `CourierAssignedEvent`

* **Producer:** `dispatch-assignment-service` (:8084)
* **Routing Key:** `dispatch.courier.assigned`
* **Destination Queues:** `delivery.dispatch-assigned.queue`, `dispatch.events.queue`, `analytics.events.queue`
* **Consumers:** `delivery-execution-service`, `analytics-ai-service` (not `notification-service`: its queue does not bind `dispatch.*`)

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Deduplication event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `orderId` | `UUID` | No | Assigned order identifier | Safe |
| `courierId` | `UUID` | No | Assigned courier identity UUID | Safe |
| `courierName` | `String` | No | Courier callsign / first name for display | Safe |
| `estimatedPickupMinutes` | `int` | No | SLA estimated pickup ETA in minutes | Safe |
| `assignedAt` | `Instant` | No | Assignment timestamp | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |
| `trackingNumber` | `String` | Yes | Tracking number (Member 9 & 5 additive field) | Safe |
| `merchantId` | `UUID` | Yes | Merchant identifier (Member 9 & 7 additive field) | Safe |
| `customerId` | `UUID` | Yes | Customer who owns the parcel, so later status events can notify them | Safe |
| `pickupDistrict` | `SriLankanDistrict` | Yes | Pickup district | Safe |
| `deliveryDistrict` | `SriLankanDistrict` | Yes | Delivery district | Safe |

#### Example JSON Payload
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "courierId": "8b7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e",
  "courierName": "Kamal Perera",
  "estimatedPickupMinutes": 18,
  "assignedAt": "2026-11-13T15:00:00Z",
  "timestamp": "2026-11-13T15:00:01Z",
  "trackingNumber": "SD-7K4M-9QX2",
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "customerId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "pickupDistrict": "COLOMBO",
  "deliveryDistrict": "KANDY"
}
```

---

### 2.5 `merchant.approved` — `MerchantApprovedEvent`

* **Producer:** `merchant-b2b-service` (:8086)
* **Routing Key:** `merchant.approved`
* **Destination Queues:** `iam.merchant-approved.queue`, `notification.events.queue`
* **Consumers:** `auth-iam-service`, `notification-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `merchantId` | `UUID` | No | Approved merchant organization UUID | Safe |
| `ownerUserId` | `UUID` | No | User UUID of the merchant account administrator | Safe |
| `approvedAt` | `Instant` | No | Approval timestamp | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e",
  "correlationId": "e0d1c2b3-a4f5-6789-0123-456789abcdef",
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "ownerUserId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "approvedAt": "2026-11-13T11:20:00Z",
  "timestamp": "2026-11-13T11:20:01Z"
}
```

---

### 2.6 `cash.transaction.recorded` — `CashTransactionRecordedEvent`

* **Producer:** `delivery-execution-service` (:8085)
* **Routing Key:** `cash.transaction.recorded`
* **Destination Queues:** `analytics.events.queue`
* **Consumers:** `analytics-ai-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `transactionId` | `UUID` | No | Ledger transaction UUID | Safe |
| `orderId` | `UUID` | No | Associated order identifier | Safe |
| `trackingNumber` | `String` | No | Tracking number | Safe |
| `merchantId` | `UUID` | Yes | Merchant identifier for settlement | Safe |
| `courierId` | `UUID` | No | Courier collecting/remitting cash | Safe |
| `type` | `String` (Enum) | No | `COLLECTION`, `REMITTANCE`, or `REFUND` | Safe |
| `amount` | `BigDecimal` | No | Cash transaction amount | Safe |
| `currency` | `String` | No | ISO currency code (`LKR`) | Safe |
| `timestamp` | `Instant` | No | Ledger entry timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "6c7d8e9f-0a1b-2c3d-4e5f-6a7b8c9d0e1f",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "transactionId": "7d8e9f0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "trackingNumber": "SD-7K4M-9QX2",
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "courierId": "8b7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e",
  "type": "COLLECTION",
  "amount": 2500.00,
  "currency": "LKR",
  "timestamp": "2026-11-13T16:15:35Z"
}
```

---

### 2.7 `parcel.fee.revised` — `ParcelFeeRevisedEvent`

* **Producer:** `delivery-execution-service` (:8085)
* **Routing Key:** `parcel.fee.revised`
* **Destination Queues:** `customer-order.fee-revision.queue`, `swiftdrop.gateway.fee.queue`
* **Consumers:** `customer-order-service`, `api-gateway`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `trackingNumber` | `String` | No | Tracking number | Safe |
| `orderId` | `UUID` | No | Order identifier | Safe |
| `customerId` | `UUID` | Yes | Customer to notify about the revision | Safe |
| `originalFee` | `BigDecimal` | No | Initial fee before revision | Safe |
| `revisedFee` | `BigDecimal` | No | New calculated shipping fee | Safe |
| `currency` | `String` | No | Currency code (`LKR`) | Safe |
| `reason` | `String` | No | Cause of revision (e.g. volumetric weight adjustment) | Safe |
| `status` | `FeeRevisionStatus` | No | `PENDING` (default), `APPROVED`, `REJECTED` or `EXPIRED` | Safe |
| `timestamp` | `Instant` | No | Revision timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "8e9f0a1b-2c3d-4e5f-6a7b-8c9d0e1f2a3b",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "trackingNumber": "SD-7K4M-9QX2",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "originalFee": 650.00,
  "revisedFee": 850.00,
  "currency": "LKR",
  "reason": "Measured volumetric weight (3.2 kg) exceeded declared weight (1.0 kg)",
  "status": "PENDING",
  "timestamp": "2026-11-13T15:30:00Z"
}
```

---

### 2.8 `webhook.dispatch.requested` — `WebhookDispatchRequestedEvent`

* **Producer:** Any business service
* **Routing Key:** `webhook.dispatch.requested`
* **Destination Queues:** `webhook.events.queue`
* **Consumers:** `webhook-resiliency-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `webhookId` | `UUID` | Yes | Webhook subscription to deliver to | Safe |
| `merchantId` | `UUID` | No | Target merchant subscription identity | Safe |
| `targetUrl` | `String` | Yes | Merchant endpoint URL | Safe |
| `httpMethod` | `String` | Yes | HTTP method, normally `POST` | Safe |
| `eventType` | `String` | No | Domain trigger name (e.g. `order.delivered`) | Safe |
| `payload` | `Map<String, Object>` | No | Sanitized webhook payload map | Sanitized |
| `headers` | `Map<String, String>` | Yes | Extra HTTP headers for the delivery | May hold merchant secrets; never log |
| `maxRetries` | `int` | No | Retry budget before dead-lettering | Safe |
| `timestamp` | `Instant` | No | Request timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "9f0a1b2c-3d4e-5f6a-7b8c-9d0e1f2a3b4c",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "eventType": "order.delivered",
  "payload": {
    "trackingNumber": "SD-7K4M-9QX2",
    "status": "DELIVERED",
    "deliveredAt": "2026-11-13T16:15:30Z"
  },
  "timestamp": "2026-11-13T16:15:32Z"
}
```

---

### 2.9 `notification.auth.otp` — `AuthOtpRequestedEvent`

* **Producer:** `auth-iam-service` (:8081)
* **Routing Key:** `notification.auth.otp` (deliberately under `notification.` so the existing `notification.#` binding delivers it)
* **Destination Queues:** `notification.events.queue`
* **Consumers:** `notification-service` (`AuthOtpTrigger`)

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier (deduplication) | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `recipientId` | `UUID` | No | IAM user the code is for; notification-service resolves the registered email from IAM | Safe |
| `purpose` | `OtpPurpose` | No | `EMAIL_VERIFICATION` or `PASSWORD_RESET`; selects the email template | Safe |
| `code` | `String` | No | 6-digit one-time code | **Secret**: never logged, stored in dispatch logs or copied to the in-app inbox |
| `expiresInMinutes` | `int` | No | Code lifetime shown in the email | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

The email address never travels on the bus. notification-service sends the code **only** to the
registered IAM address (never to a preference or request-supplied address), ignores the user's
email-off preference for these security emails, and stores a redacted body in the audit log.
These templates cannot be triggered through `POST /api/v1/notifications/logs/dispatch`.

#### Example JSON Payload
```json
{
  "eventId": "6d7e8f90-a1b2-4c3d-8e9f-0a1b2c3d4e5f",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "recipientId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "purpose": "PASSWORD_RESET",
  "code": "482913",
  "expiresInMinutes": 10,
  "timestamp": "2026-11-16T09:30:00Z"
}
```

---

### 2.10 `dispatch.failed` — `DispatchFailedEvent`

* **Producer:** `dispatch-assignment-service` (:8084)
* **Routing Key:** `dispatch.failed`
* **Destination Queues:** `dispatch.events.queue`, `analytics.events.queue` (via `dispatch.#`)
* **Consumers:** `dispatch-assignment-service`, `analytics-ai-service`

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `orderId` | `UUID` | No | Order that could not be assigned | Safe |
| `failureReason` | `String` | No | Why no courier could be assigned | Safe |
| `attemptCount` | `int` | No | Assignment attempts so far | Safe |
| `retryable` | `boolean` | No | Whether dispatch will try again | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

#### Example JSON Payload
```json
{
  "eventId": "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "failureReason": "No available courier in KANDY",
  "attemptCount": 3,
  "retryable": true,
  "timestamp": "2026-11-16T10:05:00Z"
}
```

---

### 2.11 `delivery.state.changed` — `DeliveryStateChangedEvent`

* **Producer:** `delivery-execution-service` (:8085), on every delivery state-machine transition
* **Routing Key:** `delivery.state.changed`
* **Destination Queues:** `delivery.events.queue` (via `delivery.#`)
* **Consumers:** `delivery-execution-service` (internal). Public and customer-facing updates use `parcel.status.changed.*` (section 2.3) instead.

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `deliveryId` | `UUID` | No | Delivery record identifier | Safe |
| `orderId` | `UUID` | No | Order identifier | Safe |
| `courierId` | `UUID` | Yes | Assigned courier | Safe |
| `previousState` | `DeliveryState` | Yes | State before the transition | Safe |
| `newState` | `DeliveryState` | No | State after the transition | Safe |
| `notes` | `String` | Yes | Operator or courier note | Free text; do not expose publicly |
| `timestamp` | `Instant` | No | Transition timestamp | Safe |

A cross-district parcel adds two states: `AT_ORIGIN_HUB` (handed in at the pickup district's hub) and
`AT_DESTINATION_HUB` (accepted at the drop-off district's hub). On `parcel.status.changed` they and the
line-haul `IN_TRANSIT` all appear as public `IN_TRANSIT`, told apart by `internalSubStatus` `AT_HUB`,
`OUTBOUND` and `INCOMING`. When the last-mile courier is assigned at the drop-off hub, the state stays
`AT_DESTINATION_HUB` and no status event is published.

#### Example JSON Payload
```json
{
  "eventId": "8b9c0d1e-2f3a-4b4c-9d5e-6f7a8b9c0d1e",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "deliveryId": "4c5d6e7f-8091-4a2b-b3c4-d5e6f708192a",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "courierId": "8b7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e",
  "previousState": "IN_TRANSIT",
  "newState": "OUT_FOR_DELIVERY",
  "notes": null,
  "timestamp": "2026-11-16T11:20:00Z"
}
```

---

### 2.12 `notification.auth.login` — `AuthLoginEvent`

* **Producer:** `auth-iam-service` (:8081): after a successful sign-in (password, second factor or Google), and after a wrong password for an **existing** account. notification-service emails a success alert only for a device and network not used in the last 30 days
* **Routing Key:** `notification.auth.login` (under `notification.` so the existing `notification.#` binding delivers it)
* **Destination Queues:** `notification.events.queue`
* **Consumers:** `notification-service` (`AuthLoginTrigger`)

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier (deduplication) | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `recipientId` | `UUID` | No | IAM user the alert is for; notification-service resolves the registered email from IAM | Safe |
| `outcome` | `LoginOutcome` | No | `SUCCESS` or `FAILED` (wrong password) | Safe |
| `ipAddress` | `String` | Yes | Client IP; the email shows it partly masked (`203.94.x.x`) | **PII**: never logged |
| `userAgent` | `String` | Yes | Browser user agent (max 300 characters); the email shows "Chrome on Windows" | Low |
| `occurredAt` | `Instant` | No | When the sign-in happened | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

Failed attempts for unknown usernames publish nothing, so the event never reveals whether an
account exists. notification-service emails at most one failed-sign-in alert per account every
15 minutes, sends sign-in alerts even when email notifications are off, sends them only to the
registered address, and never copies them to the in-app inbox.

#### Example JSON Payload
```json
{
  "eventId": "0a1b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c3d",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "recipientId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "outcome": "FAILED",
  "ipAddress": "203.94.12.7",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0 Safari/537.36",
  "occurredAt": "2026-11-19T15:56:00Z",
  "timestamp": "2026-11-19T15:56:00Z"
}
```

---

### 2.13 `notification.auth.invite` — `AuthStaffInviteEvent`

* **Producer:** `auth-iam-service` (:8081): when a dispatcher registers a courier (`POST /api/v1/users/couriers`), an admin creates an account without a password (`POST /api/v1/users`), or an invite is re-sent
* **Routing Key:** `notification.auth.invite` (under `notification.` so the existing `notification.#` binding delivers it)
* **Destination Queues:** `notification.events.queue`
* **Consumers:** `notification-service` (`StaffInviteTrigger`, template `STAFF_INVITE`)

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier (deduplication) | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `recipientId` | `UUID` | No | IAM user the invite is for; notification-service resolves the registered email from IAM | Safe |
| `role` | `UserRole` | No | Role of the invited account (`COURIER` or `DISPATCHER`) | Safe |
| `inviteUrl` | `String` | No | `<FRONTEND_URL>/invite#token=<43 chars>`; the token is in the fragment so it never reaches a server log | **Secret**: signs the person in; never logged, stored in dispatch logs or copied to the in-app inbox |
| `expiresInHours` | `int` | No | Link lifetime shown in the email (72) | Safe |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

IAM stores only the SHA-256 of the token. Accepting the invite (`POST /api/v1/auth/invite/accept`)
sets the password, clears the token and signs the person in; a new invite replaces the old one. The
email renders the link only when it matches the invite-link format, and like the one-time codes it
goes only to the registered address, ignores the email-off preference and is redacted in the audit log.

#### Example JSON Payload
```json
{
  "eventId": "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "recipientId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "role": "COURIER",
  "inviteUrl": "https://app.example.com/invite#token=q3Z8s1Lk0Vd9rT2yWm5nB7xC4pF6hJ1aE0uGiKoYtRs",
  "expiresInHours": 72,
  "timestamp": "2026-11-21T09:00:00Z"
}
```

---

### 2.14 `notification.merchant.application` — `MerchantApplicationEvent`

* **Producer:** `merchant-b2b-service` (:8086): when a business application is submitted or resubmitted after a rejection (`POST /api/v1/merchants/register`), approved or rejected
* **Routing Key:** `notification.merchant.application` (under `notification.` so the existing `notification.#` binding delivers it). Approval still also publishes `merchant.approved` for IAM.
* **Destination Queues:** `notification.events.queue`
* **Consumers:** `notification-service` (`MerchantApplicationTrigger`)

#### Field Specification

| Field Name | Type | Nullable | Description | Privacy / PII |
|---|---|---|---|---|
| `eventId` | `UUID` | No | Unique event identifier (deduplication) | Safe |
| `correlationId` | `UUID` | No | Trace correlation identifier | Safe |
| `merchantId` | `UUID` | No | The merchant application | Safe |
| `recipientId` | `UUID` | No | IAM user id of the owner; notification-service resolves the email from IAM | Safe |
| `businessName` | `String` | No | Shown in the alert and the email | Low |
| `operatingDistrict` | `SriLankanDistrict` | No | Pickup district; decides which dispatcher is alerted | Safe |
| `outcome` | `Outcome` | No | `SUBMITTED`, `APPROVED` or `REJECTED` | Safe |
| `reason` | `String` | Yes | Rejection reason, shown to the owner | Low |
| `timestamp` | `Instant` | No | Event publication timestamp | Safe |

`SUBMITTED` writes an in-app alert to the team audience `DISTRICT_OPS:<district>`, which only that
district's dispatcher sees. `APPROVED` and `REJECTED` email the owner (`MERCHANT_APPROVED`,
`MERCHANT_REJECTED`) and add an in-app row.

#### Example JSON Payload
```json
{
  "eventId": "8f9a0b1c-2d3e-4f4a-9b5c-6d7e8f9a0b1c",
  "correlationId": "c0a80101-8b2e-4b71-9c8e-3a1b2c3d4e5f",
  "merchantId": "5d6e7f80-9a0b-4c1d-8e2f-3a4b5c6d7e8f",
  "recipientId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "businessName": "Kandy Valley Spice Co.",
  "operatingDistrict": "KANDY",
  "outcome": "REJECTED",
  "reason": "The registration number does not match the business name.",
  "timestamp": "2026-11-21T10:00:00Z"
}
```

---

## 3. Who receives which notification

Rule: a notification goes only to the person or team the event is about, and only when it tells
them something new or asks them to act. Routine steps nobody acts on (in transit, a retry that
dispatch makes by itself, an order the merchant created themselves) send nothing.

| Event | Customer (own orders) | Courier | Merchant company (team inbox) | Operations team (dispatchers and admins) |
|---|---|---|---|---|
| `order.created` | Email + in-app "Order confirmed" | | (nothing: they created it) | |
| `dispatch.courier.assigned` | | Email + in-app + lock screen "New delivery assigned" (the assigned courier only) | In-app "Courier assigned" (have the parcel ready) | |
| `parcel.status.changed` PICKED_UP | Email + in-app + lock screen "Picked up" | | | |
| `parcel.status.changed` OUT_FOR_DELIVERY | Email + in-app + lock screen "Out for delivery" | | | |
| `parcel.status.changed` DELIVERED | Email + in-app + lock screen "Delivered" | | | |
| `parcel.status.changed` FAILED | Email + in-app + lock screen "Delivery failed" | | In-app "Delivery failed" | In-app "Delivery failed" (arrange a new attempt or a return) |
| `dispatch.failed` (retryable = false only) | | | | In-app "Dispatch needs attention" |
| `notification.auth.otp` | Email only | Email only | Email only (to the signing-in user) | Email only |
| `notification.auth.login` SUCCESS (new device or network only) | Email only | Email only | Email only (to the signing-in user) | Email only |
| `notification.auth.login` FAILED (max 1 per 15 min) | Email only | Email only | Email only (to the account owner) | Email only |
| `notification.auth.invite` | | Email only (the invited courier) | | Email only (an invited dispatcher) |
| `notification.merchant.application` SUBMITTED | | | | In-app "New merchant application" (that district's dispatcher only) |
| `notification.merchant.application` APPROVED / REJECTED | | | Email + in-app (the business owner) | |

Merchant orders: customer-order-service stores the merchant id in `customerId`, so parcel events for
a merchant order name the company, not a person. notification-service records merchant ids from
`order.created` events where `merchantId` = `customerId` (`merchant_accounts`, V10). Customer
notifications addressed to a merchant id are not sent (nothing is emailed to a company id); the
merchant team inbox gets the merchant rows instead. A merchant row is written only for a known
merchant company.

A successful sign-in is emailed only when the device and network (`"Chrome on Windows · 203.94.x.x"`,
kept in the dispatch log `reference_id`) were not used by that account in the last 30 days.

**Lock screen (Web Push).** Sent only to devices where the customer or courier turned on
"Lock-screen notifications" (table `push_subscriptions`, V11), and only while the account's push
preference (`push_enabled`) is on. Types: picked up, out for delivery, delivered, delivery failed
(customers, normal urgency, 24 h TTL, opens `/customer/tracking/{trackingNumber}`) and new delivery
assigned (couriers, high urgency, 15 min TTL, opens `/courier/deliveries`). The payload is only
`{title, body, url, tag}` with the tracking number as tag: no names, addresses, phone numbers or
amounts. Order created, team alerts and every security message are never pushed. The in-app inbox is
always written, whatever the push preference. Without `WEB_PUSH_*` keys device push is off and
everything else works.

Team inboxes (`in_app_notifications.audience`): `OPERATIONS` is shown to every DISPATCHER and
SYSTEM_ADMIN; `MERCHANT:<merchantId>` to every user of that merchant company. A team
notification's read state is shared by the team.
