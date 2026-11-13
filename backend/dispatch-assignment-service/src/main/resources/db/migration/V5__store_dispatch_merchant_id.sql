ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS merchant_id UUID;
