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
  "timestamp": "2026-11-13T14:30:00Z"
}
```

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
* **Destination Queues:** `delivery.dispatch-assigned.queue`, `analytics.events.queue`
* **Consumers:** `delivery-execution-service`, `analytics-ai-service`, `notification-service`

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
  "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
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
| `originalFee` | `BigDecimal` | No | Initial fee before revision | Safe |
| `revisedFee` | `BigDecimal` | No | New calculated shipping fee | Safe |
| `currency` | `String` | No | Currency code (`LKR`) | Safe |
| `reason` | `String` | No | Cause of revision (e.g. volumetric weight adjustment) | Safe |
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
| `merchantId` | `UUID` | No | Target merchant subscription identity | Safe |
| `eventType` | `String` | No | Domain trigger name (e.g. `order.delivered`) | Safe |
| `payload` | `Map<String, Object>` | No | Sanitized webhook payload map | Sanitized |
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

