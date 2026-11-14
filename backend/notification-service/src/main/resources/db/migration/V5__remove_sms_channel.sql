-- V5__remove_sms_channel.sql
-- SMS is no longer a notification channel; EMAIL (Azure Communication Services) and PUSH (in-app) remain.
-- Historical rows in notification_dispatch_logs keep channel = 'SMS' as an audit record.

-- 1. Templates that were seeded for SMS are now delivered by email.
UPDATE notification_templates SET channel = 'EMAIL' WHERE channel = 'SMS';

-- 2. Drop the SMS preference and phone destination; phone numbers are no longer stored here.
ALTER TABLE recipient_preferences DROP COLUMN IF EXISTS sms_enabled;
ALTER TABLE recipient_preferences DROP COLUMN IF EXISTS phone_number;
