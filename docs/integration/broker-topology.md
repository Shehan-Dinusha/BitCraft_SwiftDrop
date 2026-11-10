# SwiftDrop RabbitMQ Broker Topology

> Generated from all 10 service `RabbitMQConfig.java` files and `infra/local/rabbitmq/definitions.json`.
> Last updated: 2026-11-10.

## Exchanges

| Exchange | Type | Durable | Purpose |
|---|---|---|---|
| `swiftdrop.events.exchange` | `topic` | ✅ | Primary domain event exchange |
| `swiftdrop.dlx.exchange` | `topic` | ✅ | Dead-letter exchange for failed messages |

## Queues

| Queue | Owner Service | Quorum | DLX Routing Key | TTL |
|---|---|---|---|---|
| `order.events.queue` | customer-order-service | ✅ | `dlq.order.events` | 24 h |
| `dlq.order.events.queue` | customer-order-service | ✅ | — | 7 d |
| `customer-order.fee-revision.queue` | customer-order-service | ✅ | — | — |
| `dispatch.events.queue` | dispatch-assignment-service | ✅ | `dlq.dispatch.events` | 24 h |
| `dispatch.order-created.queue` | dispatch-assignment-service | ✅ | `dlq.dispatch.events` | 24 h |
| `dispatch.order-cancelled.queue` | dispatch-assignment-service | ✅ | `dlq.dispatch.events` | 24 h |
| `dispatch.parcel-status.queue` | dispatch-assignment-service | ✅ | `dlq.dispatch.events` | 24 h |
| `dlq.dispatch.events.queue` | dispatch-assignment-service | ✅ | — | 7 d |
| `delivery.events.queue` | delivery-execution-service | ✅ | `dlq.delivery.events` | 24 h |
| `delivery.dispatch-assigned.queue` | delivery-execution-service | ✅ | `dlq.delivery.events` | 24 h |
| `dlq.delivery.events.queue` | delivery-execution-service | ✅ | — | 7 d |
| `notification.events.queue` | notification-service | ✅ | `dlq.notification.events` | 24 h |
| `dlq.notification.events.queue` | notification-service | ✅ | — | 7 d |
| `webhook.events.queue` | webhook-resiliency-service | ✅ | `dlq.webhook.events` | 24 h |
| `dlq.webhook.events.queue` | webhook-resiliency-service | ✅ | — | 7 d |
| `analytics.events.queue` | analytics-ai-service | ✅ | `dlq.analytics.events` | 24 h |
| `dlq.analytics.events.queue` | analytics-ai-service | ✅ | — | 7 d |
| `swiftdrop.gateway.tracking.queue` | api-gateway | ✅ | — | — |
| `swiftdrop.gateway.fee.queue` | api-gateway | ✅ | — | — |
| `swiftdrop.auth.events` | auth-iam-service | ✅ | — | — |
| `iam.merchant-approved.queue` | auth-iam-service | ✅ | — | — |

## Bindings (`swiftdrop.events.exchange` → queue)

| Routing Key | Destination Queue | Consumer Service |
|---|---|---|
| `order.#` | `order.events.queue` | customer-order-service |
| `order.created` | `dispatch.order-created.queue` | dispatch-assignment-service |
| `order.cancelled` | `dispatch.order-cancelled.queue` | dispatch-assignment-service |
| `parcel.status.changed.#` | `dispatch.parcel-status.queue` | dispatch-assignment-service |
| `dispatch.courier.assigned` | `delivery.dispatch-assigned.queue` | delivery-execution-service |
| `delivery.#` | `delivery.events.queue` | delivery-execution-service |
| `parcel.status.changed.#` | `notification.events.queue` | notification-service |
| `notification.#` | `notification.events.queue` | notification-service |
| `order.created` | `notification.events.queue` | notification-service |
| `parcel.status.changed.#` | `webhook.events.queue` | webhook-resiliency-service |
| `order.created` | `webhook.events.queue` | webhook-resiliency-service |
| `webhook.dispatch.requested` | `webhook.events.queue` | webhook-resiliency-service |
| `parcel.status.changed.#` | `swiftdrop.gateway.tracking.queue` | api-gateway |
| `parcel.fee.revised` | `swiftdrop.gateway.fee.queue` | api-gateway |
| `parcel.fee.revised` | `customer-order.fee-revision.queue` | customer-order-service |
| `parcel.status.changed.#` | `analytics.events.queue` | analytics-ai-service |
| `dispatch.#` | `analytics.events.queue` | analytics-ai-service |
| `auth.#` | `swiftdrop.auth.events` | auth-iam-service |
| `merchant.approved` | `iam.merchant-approved.queue` | auth-iam-service |

## DLX Bindings (`swiftdrop.dlx.exchange` → DLQ)

| Routing Key | Destination DLQ |
|---|---|
| `dlq.order.events` | `dlq.order.events.queue` |
| `dlq.dispatch.events` | `dlq.dispatch.events.queue` |
| `dlq.delivery.events` | `dlq.delivery.events.queue` |
| `dlq.notification.events` | `dlq.notification.events.queue` |
| `dlq.webhook.events` | `dlq.webhook.events.queue` |
| `dlq.analytics.events` | `dlq.analytics.events.queue` |

## Event Routing Keys (`EventRoutingKeys.java`)

| Constant | Value | Producer(s) |
|---|---|---|
| `ORDER_CREATED` | `order.created` | customer-order-service |
| `ORDER_CANCELLED` | `order.cancelled` | customer-order-service |
| `PARCEL_STATUS_CHANGED` | `parcel.status.changed` | delivery-execution-service |
| `PARCEL_FEE_REVISED` | `parcel.fee.revised` | delivery-execution-service |
| `CASH_TRANSACTION_RECORDED` | `cash.transaction.recorded` | delivery-execution-service |
| `DISPATCH_COURIER_ASSIGNED` | `dispatch.courier.assigned` | dispatch-assignment-service |
| `DISPATCH_FAILED` | `dispatch.failed` | dispatch-assignment-service |
| `MERCHANT_APPROVED` | `merchant.approved` | merchant-b2b-service |

## Policy

| Name | Pattern | Apply To | delivery-limit |
|---|---|---|---|
| `ha-quorum-queues` | `.*` | `quorum_queues` | 5 |
