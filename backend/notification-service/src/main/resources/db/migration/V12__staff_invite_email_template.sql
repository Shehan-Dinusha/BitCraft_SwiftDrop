-- V12__staff_invite_email_template.sql
-- Invite email for staff accounts (couriers registered by their dispatcher, dispatchers created by an
-- admin). The link signs the person in, so like the one-time-code templates its dispatch log body is
-- redacted (SensitiveTemplates.STAFF_INVITE).

INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('STAFF_INVITE', 'EMAIL', 'Set up your SwiftDrop account',
     'A SwiftDrop account has been created for you. Open this link to choose your password: {{inviteUrl}} The link works once and expires in {{expiresInHours}} hours. If you were not expecting this email, you can ignore it.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
