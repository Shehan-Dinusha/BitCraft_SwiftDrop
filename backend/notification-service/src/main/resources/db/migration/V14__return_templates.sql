-- A failed delivery is tried again or returned; the sender is told at each turn.
UPDATE notification_templates
SET body_template = 'We could not deliver parcel {{trackingNumber}}: {{failureReason}}. {{nextStep}}'
WHERE template_code = 'PARCEL_FAILED'
  AND body_template = 'We could not deliver parcel {{trackingNumber}}: {{failureReason}}. Our operations team has been notified and will arrange the next step.';

INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('PARCEL_RETURNING', 'EMAIL', 'Your parcel is coming back to you',
     'We could not deliver parcel {{trackingNumber}}, so it is on its way back to you. The return is free.', TRUE),
    ('PARCEL_RETURNED', 'EMAIL', 'Your parcel has been returned',
     'Parcel {{trackingNumber}} was handed back on {{returnedAt}}.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
