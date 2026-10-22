CREATE TABLE IF NOT EXISTS proof_of_delivery (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL,
    photo_url VARCHAR(2048) NOT NULL,
    recipient_signature TEXT NOT NULL,
    confirmation_otp VARCHAR(32) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP WITH TIME ZONE
);
