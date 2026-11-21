-- V7__staff_invites.sql
-- Staff accounts (couriers created by their dispatcher, dispatchers created by an admin) are invited
-- by email instead of being given a password. The emailed link carries a random token; only its
-- SHA-256 hash is stored here. Accepting the invite sets the password and clears both columns, so a
-- non-null hash means the invite is still pending.
ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS invite_token_hash VARCHAR(64);
ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS invite_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_accounts_invite_token_hash
    ON user_accounts (invite_token_hash)
    WHERE invite_token_hash IS NOT NULL;

COMMENT ON COLUMN user_accounts.invite_token_hash IS
    'SHA-256 of the pending staff invite token; NULL once the invite is accepted or for self-service accounts.';
