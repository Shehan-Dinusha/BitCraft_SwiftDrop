-- V9__team_inbox_and_new_templates.sql
-- 1. Team inbox rows: a notification can belong to a team (audience) instead of one user.
--    OPERATIONS            = every dispatcher and system admin
--    MERCHANT:<merchantId> = every user of that merchant company
--    Exactly one of user_id / audience is set. A team row's read state is shared by the team.
ALTER TABLE in_app_notifications ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE in_app_notifications ADD COLUMN IF NOT EXISTS audience VARCHAR(100);
ALTER TABLE in_app_notifications ADD CONSTRAINT chk_in_app_owner
    CHECK ((user_id IS NOT NULL AND audience IS NULL) OR (user_id IS NULL AND audience IS NOT NULL));
CREATE INDEX IF NOT EXISTS idx_in_app_audience_created ON in_app_notifications(audience, created_at DESC)
    WHERE audience IS NOT NULL;

COMMENT ON COLUMN in_app_notifications.audience IS 'Team inbox (OPERATIONS or MERCHANT:<merchantId>); NULL for a personal notification';

-- 2. Email templates for couriers and sign-in security alerts.
INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('COURIER_ASSIGNED', 'EMAIL', 'New SwiftDrop delivery assigned',
     'Parcel {{trackingNumber}} has been assigned to you. Please collect it from {{pickupDistrict}} for delivery to {{deliveryDistrict}}.', TRUE),
    ('LOGIN_SUCCESS', 'EMAIL', 'New sign-in to your SwiftDrop account',
     'Your SwiftDrop account was signed in to on {{occurredAt}} from {{device}}. If this was not you, reset your password straight away.', TRUE),
    ('LOGIN_FAILED', 'EMAIL', 'Failed sign-in attempt on your SwiftDrop account',
     'Someone tried to sign in to your SwiftDrop account with a wrong password on {{occurredAt}} from {{device}}. If this was not you, consider changing your password.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
