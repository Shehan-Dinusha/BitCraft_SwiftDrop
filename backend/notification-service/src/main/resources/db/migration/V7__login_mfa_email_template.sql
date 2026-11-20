-- V7__login_mfa_email_template.sql
-- Email template for the second sign-in factor of privileged roles (auth-iam-service LOGIN_MFA).
-- Like the other one-time-code templates, its dispatch log body is redacted.

INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('LOGIN_MFA', 'EMAIL', 'Your SwiftDrop sign-in code',
     'Your SwiftDrop sign-in code is {{code}}. It expires in {{expiresInMinutes}} minutes. If you did not just try to sign in, change your password now.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
