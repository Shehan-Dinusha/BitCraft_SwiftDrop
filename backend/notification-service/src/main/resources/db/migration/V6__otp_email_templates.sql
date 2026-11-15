-- V6__otp_email_templates.sql
-- Email templates for one-time security codes sent on behalf of auth-iam-service.
-- The code itself is never stored: dispatch logs hold a redacted body for these templates.

INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('EMAIL_VERIFICATION', 'EMAIL', 'Your SwiftDrop verification code',
     'Your SwiftDrop verification code is {{code}}. It expires in {{expiresInMinutes}} minutes. If you did not create a SwiftDrop account, you can ignore this email.', TRUE),
    ('PASSWORD_RESET', 'EMAIL', 'Reset your SwiftDrop password',
     'Your SwiftDrop password reset code is {{code}}. It expires in {{expiresInMinutes}} minutes. If you did not request a password reset, ignore this email and your password will stay the same.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
