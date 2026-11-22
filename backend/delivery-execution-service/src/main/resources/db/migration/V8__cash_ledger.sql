-- Cash couriers collect: the delivery fee from the sender at pickup and cash on delivery from the
-- recipient. Entries are never edited; each one moves courier -> hub (handover) -> bank (deposit).
CREATE TABLE cash_deposits (
    id BIGSERIAL PRIMARY KEY,
    deposit_id UUID NOT NULL UNIQUE,
    district VARCHAR(64) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'LKR',
    reference VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    deposited_by UUID NOT NULL,
    deposited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_note VARCHAR(256),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_cash_deposits_district_status ON cash_deposits (district, status);

CREATE TABLE cash_handovers (
    id BIGSERIAL PRIMARY KEY,
    handover_id UUID NOT NULL UNIQUE,
    courier_id UUID NOT NULL,
    courier_name VARCHAR(128),
    district VARCHAR(64) NOT NULL,
    expected_amount NUMERIC(12, 2) NOT NULL CHECK (expected_amount > 0),
    counted_amount NUMERIC(12, 2) CHECK (counted_amount >= 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'LKR',
    status VARCHAR(16) NOT NULL,
    note VARCHAR(256),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    counted_by UUID,
    counted_at TIMESTAMP WITH TIME ZONE,
    deposit_id UUID REFERENCES cash_deposits (deposit_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_cash_handovers_courier ON cash_handovers (courier_id, submitted_at);
CREATE INDEX idx_cash_handovers_district_status ON cash_handovers (district, status);

CREATE TABLE cash_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_id UUID NOT NULL UNIQUE,
    entry_type VARCHAR(16) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    delivery_id UUID NOT NULL,
    order_id UUID NOT NULL,
    tracking_number VARCHAR(32) NOT NULL,
    courier_id UUID,
    courier_name VARCHAR(128),
    district VARCHAR(64),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'LKR',
    note VARCHAR(256),
    recorded_by UUID,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    handover_id UUID REFERENCES cash_handovers (handover_id),
    deposit_id UUID REFERENCES cash_deposits (deposit_id),
    -- A parcel's fee or cash on delivery is collected once and refunded at most once.
    CONSTRAINT uk_cash_entries_once UNIQUE (delivery_id, kind, entry_type)
);
CREATE INDEX idx_cash_entries_courier_open ON cash_entries (courier_id) WHERE handover_id IS NULL;
CREATE INDEX idx_cash_entries_district_recorded ON cash_entries (district, recorded_at);
CREATE INDEX idx_cash_entries_handover ON cash_entries (handover_id);

-- The courier who brought a cross-district parcel to the hub keeps it in their day's list.
ALTER TABLE delivery_sessions
    ADD COLUMN first_mile_courier_id UUID,
    ADD COLUMN handed_in_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_delivery_sessions_first_mile ON delivery_sessions (first_mile_courier_id, handed_in_at);
