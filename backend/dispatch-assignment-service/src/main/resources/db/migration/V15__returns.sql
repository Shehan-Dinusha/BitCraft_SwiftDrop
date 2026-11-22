-- A failed delivery waits at the hub: tried again, or returned to the sender (leg RETURN).
ALTER TABLE dispatch_assignments
    ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_reason VARCHAR(32);
