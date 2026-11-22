-- The delivery fee of each order, so a shop knows how much cash to prepare. Unknown for older orders.
ALTER TABLE merchant_order_receipts
    ADD COLUMN fee NUMERIC(10, 2),
    ADD COLUMN currency VARCHAR(8);
