-- V4: Idempotency table for processed merchant.approved events.
--
-- Prevents the IAM service from applying a merchant approval more than once
-- even if the message broker delivers the event multiple times (at-least-once
-- delivery guarantee).  The primary key on event_id acts as the deduplication
-- key.
--
-- Retention: rows can be purged after 30 days via a scheduled job without
-- impacting correctness, because the merchant_id is already stored on
-- user_accounts after first processing.

CREATE TABLE IF NOT EXISTS processed_merchant_events (
    event_id    UUID        NOT NULL,
    merchant_id UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_merchant_events PRIMARY KEY (event_id)
);

COMMENT ON TABLE  processed_merchant_events                IS 'Idempotency log for merchant.approved RabbitMQ events';
COMMENT ON COLUMN processed_merchant_events.event_id       IS 'Globally unique event identifier from MerchantApprovedEvent.eventId';
COMMENT ON COLUMN processed_merchant_events.merchant_id    IS 'The merchant UUID that was approved';
COMMENT ON COLUMN processed_merchant_events.user_id        IS 'The IAM user UUID whose merchantId was updated';
COMMENT ON COLUMN processed_merchant_events.processed_at   IS 'UTC timestamp when this IAM service instance processed the event';

