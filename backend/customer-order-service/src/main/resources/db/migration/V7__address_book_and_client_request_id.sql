-- Saved addresses: a customer's address book for the pickup and drop-off steps. Owned by this service
-- (customer_id is the IAM user id; no cross-service foreign key). Stored in the same parts the booking
-- form uses, so a saved address fills the form back exactly.
CREATE TABLE IF NOT EXISTS customer_addresses (
    id              BIGSERIAL PRIMARY KEY,
    address_id      UUID         NOT NULL UNIQUE,
    customer_id     UUID         NOT NULL,
    label           VARCHAR(40)  NOT NULL,
    contact_name    VARCHAR(100) NOT NULL,
    contact_phone   VARCHAR(16)  NOT NULL,
    alternate_phone VARCHAR(16),
    house           VARCHAR(40)  NOT NULL,
    street          VARCHAR(150) NOT NULL,
    area            VARCHAR(98),
    landmark        VARCHAR(100),
    city            VARCHAR(100) NOT NULL,
    district        VARCHAR(32)  NOT NULL,
    postal_code     VARCHAR(5)   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer ON customer_addresses(customer_id);

-- A booking retried after a dropped connection sends the same client_request_id; one order per
-- (customer, request). Merchant orders use external_ref instead and leave this empty.
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS client_request_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_orders_client_request
    ON customer_orders(customer_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
