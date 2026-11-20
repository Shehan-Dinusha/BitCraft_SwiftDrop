-- V8__in_app_notification_type.sql
-- Store which kind of notification each in-app row is (the template code, e.g. PARCEL_DELIVERED),
-- so the inbox can show a distinct icon per type instead of guessing from the title.

ALTER TABLE in_app_notifications ADD COLUMN IF NOT EXISTS notification_type VARCHAR(64);

-- Best-effort backfill for rows created before this column existed, based on the seeded titles.
UPDATE in_app_notifications SET notification_type = 'PARCEL_CREATED'
    WHERE notification_type IS NULL AND title ILIKE '%order confirmed%';
UPDATE in_app_notifications SET notification_type = 'PARCEL_PICKED_UP'
    WHERE notification_type IS NULL AND title ILIKE '%picked up%';
UPDATE in_app_notifications SET notification_type = 'PARCEL_OUT_FOR_DELIVERY'
    WHERE notification_type IS NULL AND title ILIKE '%out for delivery%';
UPDATE in_app_notifications SET notification_type = 'PARCEL_DELIVERED'
    WHERE notification_type IS NULL AND title ILIKE '%delivered%';
UPDATE in_app_notifications SET notification_type = 'PARCEL_FAILED'
    WHERE notification_type IS NULL AND (title ILIKE '%exception%' OR title ILIKE '%fail%');

COMMENT ON COLUMN in_app_notifications.notification_type IS 'Template code of the notification (e.g. PARCEL_DELIVERED); NULL for unknown legacy rows';
