-- V4__in_app_notifications.sql
-- Store per-user in-app notifications for customer/merchant notifications

CREATE TABLE IF NOT EXISTS in_app_notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    tracking_number VARCHAR(64),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_in_app_user_created ON in_app_notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_in_app_user_unread ON in_app_notifications(user_id) WHERE read = FALSE;

COMMENT ON TABLE in_app_notifications IS 'Stores in-app inbox alerts for customers and merchants';
COMMENT ON COLUMN in_app_notifications.user_id IS 'Recipient user UUID (JWT subject)';
COMMENT ON COLUMN in_app_notifications.title IS 'Notification title or header';
COMMENT ON COLUMN in_app_notifications.body IS 'Notification body text';
COMMENT ON COLUMN in_app_notifications.tracking_number IS 'Associated parcel tracking number if applicable';
COMMENT ON COLUMN in_app_notifications.read IS 'Whether the notification has been marked as read by the user';
COMMENT ON COLUMN in_app_notifications.created_at IS 'Timestamp when the notification was created';

