-- A failed delivery can be replayed any number of times, but only one replay may be waiting or running at once,
-- so a double click or two browser tabs never send the same event twice (decision D10).
CREATE UNIQUE INDEX uq_webhook_deliveries_active_replay
    ON webhook_deliveries (replay_of_delivery_id)
    WHERE replay_of_delivery_id IS NOT NULL AND status IN ('PENDING', 'IN_FLIGHT', 'RETRY');
