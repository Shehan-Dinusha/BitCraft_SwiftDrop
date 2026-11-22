-- A shop's parcels are collected together, once the shop says they are packed.
ALTER TABLE dispatch_assignments
    ADD COLUMN merchant_name VARCHAR(150),
    ADD COLUMN ready_at TIMESTAMP WITH TIME ZONE;

-- Orders booked before shops could say so are treated as packed.
UPDATE dispatch_assignments SET ready_at = created_at WHERE merchant_id IS NOT NULL;

CREATE INDEX idx_dispatch_assignments_merchant_status ON dispatch_assignments (merchant_id, status);
