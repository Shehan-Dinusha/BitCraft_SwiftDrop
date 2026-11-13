-- V4__add_recipient_name_to_proof_of_delivery.sql
-- Add recipient_name column and relax signature / OTP constraints for multipart photo delivery

ALTER TABLE proof_of_delivery
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(255),
    ALTER COLUMN recipient_signature DROP NOT NULL,
    ALTER COLUMN confirmation_otp DROP NOT NULL;

