-- V13__merchant_application_templates.sql
-- Emails to a business owner when the district dispatcher (or an admin) decides on their merchant
-- application (notification.merchant.application, outcome APPROVED / REJECTED).

INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('MERCHANT_APPROVED', 'EMAIL', 'Your SwiftDrop business account is approved',
     '{{businessName}} is approved to ship with SwiftDrop from {{district}}. Sign in to your merchant portal to create an API key and start sending orders.', TRUE),
    ('MERCHANT_REJECTED', 'EMAIL', 'Your SwiftDrop business application needs changes',
     'We could not approve {{businessName}} yet. Reason: {{reason}} Sign in to your merchant portal to correct the details and submit the application again.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
