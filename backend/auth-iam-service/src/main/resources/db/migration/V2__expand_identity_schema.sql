-- Stage 2 Auth/IAM identity expansion.
-- The V1 tables remain the source of truth for credentials; this migration adds
-- the public identity, role, district, and account lifecycle fields required by
-- the gateway and downstream services.

ALTER TABLE user_accounts
    ADD COLUMN IF NOT EXISTS public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS email VARCHAR(320),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN IF NOT EXISTS district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_id UUID,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_accounts_public_id
    ON user_accounts (public_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_accounts_username_lower
    ON user_accounts (LOWER(username));

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_accounts_email_lower
    ON user_accounts (LOWER(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_accounts_phone
    ON user_accounts (phone)
    WHERE phone IS NOT NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS replaced_by_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512),
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_role_assignments_user_role
    ON role_assignments (user_id, role_name);
