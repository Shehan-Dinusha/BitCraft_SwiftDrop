-- Proof of delivery no longer uses a recipient OTP: a photo plus recipient name (or a signature
-- on the JSON path) is the evidence, and a successful upload marks the proof verified.
ALTER TABLE proof_of_delivery
    DROP COLUMN IF EXISTS confirmation_otp;
