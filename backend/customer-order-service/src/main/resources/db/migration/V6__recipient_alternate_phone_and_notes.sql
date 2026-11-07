-- Optional second phones for the sender and the recipient and optional notes for the courier, both entered by the customer.
ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS pickup_contact_alternate_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recipient_alternate_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS customer_notes VARCHAR(500);
