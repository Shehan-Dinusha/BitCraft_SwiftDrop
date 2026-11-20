-- V6__user_avatar.sql
-- Profile pictures. The browser crops and resizes the image to 256x256 before upload, so each one
-- is small; it is kept in its own table so loading a user account never reads image bytes.
ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS user_avatars (
    user_id      BIGINT PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    image        BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_avatar_type CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    CONSTRAINT chk_user_avatar_size CHECK (octet_length(image) <= 524288)
);

COMMENT ON TABLE user_avatars IS 'Profile picture of a user (PNG, JPEG or WebP, at most 512 KB)';
