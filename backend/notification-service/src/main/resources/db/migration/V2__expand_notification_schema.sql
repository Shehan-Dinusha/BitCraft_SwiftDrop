-- V2__expand_notification_schema.sql
-- Expand notification_dispatch_logs, add recipient_preferences and notification_templates

-- 1. Recipient Preferences Table
CREATE TABLE IF NOT EXISTS recipient_preferences (
    id BIGSERIAL PRIMARY KEY,
    recipient_id UUID NOT NULL UNIQUE,
    recipient_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_address VARCHAR(255),
    phone_number VARCHAR(32),
    language_preference VARCHAR(8) NOT NULL DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recipient_pref_recipient_id ON recipient_preferences(recipient_id);

-- 2. Notification Templates Table
CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL UNIQUE,
    channel VARCHAR(32) NOT NULL,
    subject_template VARCHAR(255),
    body_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notify_tmpl_code ON notification_templates(template_code);

-- 3. Expand Notification Dispatch Logs
ALTER TABLE notification_dispatch_logs
    ADD COLUMN IF NOT EXISTS log_id UUID,
    ADD COLUMN IF NOT EXISTS recipient_id UUID,
    ADD COLUMN IF NOT EXISTS channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reference_id VARCHAR(64);

-- Populate log_id for any existing records
UPDATE notification_dispatch_logs SET log_id = gen_random_uuid() WHERE log_id IS NULL;
ALTER TABLE notification_dispatch_logs ALTER COLUMN log_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_notify_dispatch_log_id ON notification_dispatch_logs(log_id);
CREATE INDEX IF NOT EXISTS idx_notify_dispatch_recipient ON notification_dispatch_logs(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notify_dispatch_created_at ON notification_dispatch_logs(created_at DESC);

-- Seed Default Notification Templates
INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES 
    ('PARCEL_CREATED', 'SMS', 'SwiftDrop Order Confirmed', 'Your parcel {{trackingNumber}} has been scheduled for pickup. Track live on SwiftDrop.', TRUE),
    ('PARCEL_PICKED_UP', 'SMS', 'SwiftDrop Parcel Picked Up', 'Courier {{courierName}} has picked up parcel {{trackingNumber}}. In-transit to distribution hub.', TRUE),
    ('PARCEL_OUT_FOR_DELIVERY', 'SMS', 'SwiftDrop Out for Delivery', 'Parcel {{trackingNumber}} is out for delivery with {{courierName}}. Delivery expected shortly.', TRUE),
    ('PARCEL_DELIVERED', 'EMAIL', 'Parcel Delivered - SwiftDrop', 'Your parcel {{trackingNumber}} was successfully delivered at {{deliveredAt}}. Thank you for choosing SwiftDrop.', TRUE),
    ('PARCEL_FAILED', 'SMS', 'SwiftDrop Delivery Exception', 'Delivery attempt failed for parcel {{trackingNumber}}: {{failureReason}}. We will retry shortly.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
