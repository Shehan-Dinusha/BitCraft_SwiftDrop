-- The sender hears that a courier is coming for the parcel, and the recipient (by the email the sender
-- entered) follows the parcel from booking to the end. The recipient has no account and never pays anything.
INSERT INTO notification_templates (template_code, channel, subject_template, body_template, is_active)
VALUES
    ('PARCEL_PICKUP_ASSIGNED', 'EMAIL', 'A courier is coming for your parcel',
     'A courier has been assigned to collect parcel {{trackingNumber}}. Please have it packed and labelled, with the delivery fee ready in cash.', TRUE),
    ('RECIPIENT_PARCEL_CREATED', 'EMAIL', 'A parcel is on its way to you',
     '{{senderName}} is sending you parcel {{trackingNumber}} with SwiftDrop. There is nothing to pay on delivery.', TRUE),
    ('RECIPIENT_PICKED_UP', 'EMAIL', 'Your parcel has been picked up',
     'Parcel {{trackingNumber}} from {{senderName}} has been collected and is on its way to you.', TRUE),
    ('RECIPIENT_OUT_FOR_DELIVERY', 'EMAIL', 'Your parcel arrives today',
     'A courier is bringing parcel {{trackingNumber}} to you today. Please make sure someone is there to receive it. There is nothing to pay.', TRUE),
    ('RECIPIENT_DELIVERED', 'EMAIL', 'Your parcel has been delivered',
     'Parcel {{trackingNumber}} from {{senderName}} was delivered on {{deliveredAt}}.', TRUE),
    ('RECIPIENT_FAILED', 'EMAIL', 'We could not deliver your parcel',
     'We could not deliver parcel {{trackingNumber}}: {{failureReason}}. {{nextStep}}', TRUE),
    ('RECIPIENT_RETURNING', 'EMAIL', 'Your parcel is going back to the sender',
     'Parcel {{trackingNumber}} will not be delivered and is on its way back to {{senderName}}.', TRUE),
    ('RECIPIENT_CANCELLED', 'EMAIL', 'A parcel to you was cancelled',
     '{{senderName}} cancelled parcel {{trackingNumber}}, so it will not be delivered.', TRUE)
ON CONFLICT (template_code) DO NOTHING;
