-- A shop's parcels are collected in one visit and paid for once.
ALTER TABLE delivery_sessions
    ADD COLUMN merchant_id UUID,
    ADD COLUMN merchant_name VARCHAR(150);
CREATE INDEX idx_delivery_sessions_courier_merchant ON delivery_sessions (courier_id, merchant_id);

CREATE TABLE merchant_payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    merchant_id UUID NOT NULL,
    merchant_name VARCHAR(150),
    courier_id UUID,
    courier_name VARCHAR(128),
    district VARCHAR(64),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'LKR',
    parcels INTEGER NOT NULL CHECK (parcels > 0),
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_merchant_payments_merchant ON merchant_payments (merchant_id, paid_at);

ALTER TABLE cash_entries ADD COLUMN payment_id UUID REFERENCES merchant_payments (payment_id);
CREATE INDEX idx_cash_entries_payment ON cash_entries (payment_id);
