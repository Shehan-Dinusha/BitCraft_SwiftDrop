CREATE TABLE merchant_order_receipts (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    external_ref VARCHAR(128) NOT NULL,
    order_id UUID NOT NULL,
    tracking_number VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchant_order_reference UNIQUE (merchant_id, external_ref)
);
