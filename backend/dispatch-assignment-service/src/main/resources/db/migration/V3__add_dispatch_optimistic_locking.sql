-- Add optimistic locking to assignment and courier workload updates.
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE courier_shifts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- One dispatch record is allowed for each order. Partial indexing preserves
-- compatibility with legacy rows that have no order identifier.
CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatch_assignments_order_id
    ON dispatch_assignments (order_id)
    WHERE order_id IS NOT NULL;
