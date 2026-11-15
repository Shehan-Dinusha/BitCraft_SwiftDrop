-- V5: email verification flag.
--
-- Existing accounts (including the bootstrap admin, staff and Google sign-ups) are treated as
-- already verified, hence DEFAULT TRUE. Self-service password registrations set the flag to
-- FALSE in application code and become TRUE once the emailed one-time code is confirmed
-- (or the user signs in with Google, which proves ownership of the mailbox).

ALTER TABLE user_accounts
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

-- Used by the scheduled clean-up of abandoned, never-verified registrations.
CREATE INDEX IF NOT EXISTS ix_user_accounts_unverified_created
    ON user_accounts (created_at)
    WHERE email_verified = FALSE;

COMMENT ON COLUMN user_accounts.email_verified IS
    'TRUE once the owner proved control of the email address (OTP code or Google sign-in).';
