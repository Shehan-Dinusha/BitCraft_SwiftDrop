-- V3: persistent foundation for asynchronous webhook delivery.
--
-- Adds the transactional inbox (inbound_events), the order ownership projection
-- (order_merchant_map), the delivery job table (webhook_deliveries) and links attempt
-- history to delivery jobs. All changes are additive or relax a constraint; no V1/V2
-- row is rewritten or removed. Existing attempt and dead-letter rows stay as legacy
-- history (delivery_id IS NULL): they cannot be tied to a source event reliably, because
-- V2 never recorded one, so no relationship is fabricated for them.

-- ---------------------------------------------------------------------------
-- 1. Endpoint integrity
-- ---------------------------------------------------------------------------
-- V2 gave these columns placeholder defaults (a random merchant, an example URL and a
-- shared secret). An insert that omits them must fail instead of silently creating an
-- endpoint for a non-existent merchant. Existing values are untouched.
ALTER TABLE webhook_endpoints
    ALTER COLUMN merchant_id DROP DEFAULT,
    ALTER COLUMN target_url DROP DEFAULT,
    ALTER COLUMN secret_key DROP DEFAULT;

-- Referenced by the composite foreign key that pins every delivery to its endpoint's merchant.
ALTER TABLE webhook_endpoints
    ADD CONSTRAINT uq_webhook_endpoints_endpoint_merchant UNIQUE (endpoint_id, merchant_id);

-- ---------------------------------------------------------------------------
-- 2. Transactional inbox
-- ---------------------------------------------------------------------------
CREATE TABLE inbound_events (
    -- The producer's eventId. Being the primary key makes redelivery detection a
    -- constraint, not an application-level existence check.
    event_id          UUID         PRIMARY KEY,
    event_type        VARCHAR(100) NOT NULL,
    schema_version    VARCHAR(16)  NOT NULL,
    -- AMQP app-id when the producer sets one; current producers do not.
    source_service    VARCHAR(100),
    correlation_id    UUID         NOT NULL,
    order_id          UUID,
    -- The validated contract fields only, with customer identifiers removed.
    payload           JSONB        NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processing_status VARCHAR(32)  NOT NULL,
    merchant_id       UUID,
    resolved_at       TIMESTAMPTZ,
    CONSTRAINT ck_inbound_events_status
        CHECK (processing_status IN ('AWAITING_MERCHANT', 'RESOLVED', 'NO_MERCHANT')),
    CONSTRAINT ck_inbound_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    -- merchant_id is present exactly when the event was resolved to a merchant.
    CONSTRAINT ck_inbound_events_resolution CHECK (
           (processing_status = 'AWAITING_MERCHANT' AND merchant_id IS NULL     AND resolved_at IS NULL)
        OR (processing_status = 'RESOLVED'          AND merchant_id IS NOT NULL AND resolved_at IS NOT NULL)
        OR (processing_status = 'NO_MERCHANT'       AND merchant_id IS NULL     AND resolved_at IS NOT NULL)),
    -- Target of the composite foreign key from webhook_deliveries.
    CONSTRAINT uq_inbound_events_event_merchant UNIQUE (event_id, merchant_id)
);

CREATE INDEX idx_inbound_events_order ON inbound_events (order_id, occurred_at);
-- Parked events, for diagnosis ("what has been waiting longest for its order?").
CREATE INDEX idx_inbound_events_awaiting ON inbound_events (received_at)
    WHERE processing_status = 'AWAITING_MERCHANT';

COMMENT ON COLUMN inbound_events.processing_status IS
    'AWAITING_MERCHANT: order.created for order_id not yet seen; resolved automatically when it arrives. '
    'RESOLVED: merchant known, deliveries created for matching endpoints (possibly none). '
    'NO_MERCHANT: the order has no merchant (customer order); nothing to deliver.';

-- ---------------------------------------------------------------------------
-- 3. Order ownership projection (fed by order.created)
-- ---------------------------------------------------------------------------
CREATE TABLE order_merchant_map (
    -- The orderId from order.created. dispatch and delivery propagate this same id into
    -- parcel.status.changed, so it is the only key status events can be resolved by.
    order_id        UUID        PRIMARY KEY,
    -- NULL means the order is known and belongs to no merchant (a customer order).
    merchant_id     UUID,
    -- Informational only. delivery-execution-service publishes its own tracking number,
    -- so this must never be used to resolve status events.
    tracking_number VARCHAR(64),
    source_event_id UUID        NOT NULL REFERENCES inbound_events (event_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_merchant_map_merchant ON order_merchant_map (merchant_id)
    WHERE merchant_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Delivery jobs
-- ---------------------------------------------------------------------------
CREATE TABLE webhook_deliveries (
    delivery_id           UUID         PRIMARY KEY,
    inbound_event_id      UUID         NOT NULL,
    endpoint_id           UUID         NOT NULL,
    merchant_id           UUID         NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt_count         INT          NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ,
    lease_owner           VARCHAR(128),
    lease_expires_at      TIMESTAMPTZ,
    replay_of_delivery_id UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Tenant isolation enforced by the database: a delivery's merchant must be both the
    -- merchant its event was resolved to and the merchant owning the endpoint.
    CONSTRAINT fk_webhook_deliveries_event_merchant
        FOREIGN KEY (inbound_event_id, merchant_id) REFERENCES inbound_events (event_id, merchant_id),
    -- No cascade: an endpoint with delivery history cannot be hard-deleted.
    CONSTRAINT fk_webhook_deliveries_endpoint_merchant
        FOREIGN KEY (endpoint_id, merchant_id) REFERENCES webhook_endpoints (endpoint_id, merchant_id),
    CONSTRAINT fk_webhook_deliveries_replay_of
        FOREIGN KEY (replay_of_delivery_id) REFERENCES webhook_deliveries (delivery_id),
    CONSTRAINT ck_webhook_deliveries_status
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'SUCCEEDED', 'RETRY', 'DEAD')),
    CONSTRAINT ck_webhook_deliveries_attempt_count CHECK (attempt_count >= 0),
    -- A job waiting to run must say when it may run.
    CONSTRAINT ck_webhook_deliveries_schedule
        CHECK (status NOT IN ('PENDING', 'RETRY') OR next_attempt_at IS NOT NULL),
    -- A lease exists exactly while a worker holds the job.
    CONSTRAINT ck_webhook_deliveries_lease
        CHECK ((status = 'IN_FLIGHT') = (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)),
    CONSTRAINT ck_webhook_deliveries_replay_not_self
        CHECK (replay_of_delivery_id IS NULL OR replay_of_delivery_id <> delivery_id)
);

-- One original delivery per (event, endpoint). Replays are separate rows that point at
-- the delivery they replay, so they are excluded from this rule.
CREATE UNIQUE INDEX uq_webhook_deliveries_event_endpoint_original
    ON webhook_deliveries (inbound_event_id, endpoint_id)
    WHERE replay_of_delivery_id IS NULL;

-- Due-job lookup for the future worker (ORDER BY next_attempt_at ... FOR UPDATE SKIP LOCKED).
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries (next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY');
-- Expired-lease lookup for the future recovery job.
CREATE INDEX idx_webhook_deliveries_lease_expiry ON webhook_deliveries (lease_expires_at)
    WHERE status = 'IN_FLIGHT';
CREATE INDEX idx_webhook_deliveries_event ON webhook_deliveries (inbound_event_id);
CREATE INDEX idx_webhook_deliveries_merchant ON webhook_deliveries (merchant_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_endpoint ON webhook_deliveries (endpoint_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_dead ON webhook_deliveries (merchant_id, updated_at DESC)
    WHERE status = 'DEAD';
CREATE INDEX idx_webhook_deliveries_replay_of ON webhook_deliveries (replay_of_delivery_id)
    WHERE replay_of_delivery_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. Attempt history linked to delivery jobs
-- ---------------------------------------------------------------------------
ALTER TABLE webhook_delivery_attempts
    ADD COLUMN delivery_id UUID REFERENCES webhook_deliveries (delivery_id),
    ADD COLUMN error_class VARCHAR(32),
    ADD COLUMN latency_ms  INT,
    -- New attempts read the payload through their delivery's inbound event.
    ALTER COLUMN payload DROP NOT NULL;

ALTER TABLE webhook_delivery_attempts
    -- Exactly one of: legacy row (own payload copy, no job) or job attempt (no payload copy).
    ADD CONSTRAINT ck_webhook_delivery_attempts_model
        CHECK ((delivery_id IS NULL) <> (payload IS NULL)),
    ADD CONSTRAINT ck_webhook_delivery_attempts_error_class
        CHECK (error_class IS NULL OR error_class IN (
            'TIMEOUT', 'CONNECTION_FAILURE', 'HTTP_CLIENT_ERROR', 'HTTP_RATE_LIMITED',
            'HTTP_SERVER_ERROR', 'INVALID_RESPONSE', 'BLOCKED_DESTINATION', 'UNKNOWN')),
    ADD CONSTRAINT ck_webhook_delivery_attempts_latency
        CHECK (latency_ms IS NULL OR latency_ms >= 0);

-- NOT VALID: enforced for new rows only, so unusual legacy rows cannot block the upgrade.
ALTER TABLE webhook_delivery_attempts
    ADD CONSTRAINT ck_webhook_delivery_attempts_number CHECK (attempt_number >= 1) NOT VALID;

CREATE UNIQUE INDEX uq_webhook_delivery_attempts_delivery_number
    ON webhook_delivery_attempts (delivery_id, attempt_number)
    WHERE delivery_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. Legacy dead-letter table
-- ---------------------------------------------------------------------------
COMMENT ON TABLE dead_letter_webhook_records IS
    'Legacy dead-letter history written by the synchronous dispatcher (test pings and legacy replays). '
    'Delivery jobs record dead letters as webhook_deliveries.status = DEAD; this table is not their source of truth.';
