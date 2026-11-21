-- V11__push_subscriptions.sql
-- Browser Web Push subscriptions: one row per device (a user can have several). The endpoint is unique,
-- so a device that signs in as someone else moves to that user instead of alerting both.
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    UUID          NOT NULL,
    endpoint        VARCHAR(2048) NOT NULL,
    p256dh          VARCHAR(128)  NOT NULL,
    auth            VARCHAR(64)   NOT NULL,
    user_agent      VARCHAR(300),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_success_at TIMESTAMPTZ,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX IF NOT EXISTS idx_push_subscriptions_recipient ON push_subscriptions(recipient_id);

COMMENT ON TABLE push_subscriptions IS 'Web Push subscriptions per device (endpoint + p256dh/auth keys from PushSubscription.toJSON())';
