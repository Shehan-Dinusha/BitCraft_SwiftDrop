-- V10__merchant_accounts_and_template_wording.sql
-- 1. Merchant companies seen on merchant orders. For a merchant order customer-order-service stores the
--    merchant id in customerId, so parcel status events carry a company id, not a person. Knowing which ids
--    are merchant companies lets notification-service send those updates to the merchant team inbox instead
--    of emailing an id that has no address.
CREATE TABLE IF NOT EXISTS merchant_accounts (
    merchant_id   UUID PRIMARY KEY,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE merchant_accounts IS 'Merchant company ids seen on merchant orders (order.created with merchantId = customerId)';

-- 2. Clearer wording. The template body is the in-app message and the plain-text email.
--    Only rows still holding the original seed text are changed, so an edited template is kept.
UPDATE notification_templates
SET subject_template = 'Your SwiftDrop order is confirmed',
    body_template    = 'Order {{trackingNumber}} is confirmed. We will let you know when a courier collects it.'
WHERE template_code = 'PARCEL_CREATED'
  AND body_template = 'Your parcel {{trackingNumber}} has been scheduled for pickup. Track live on SwiftDrop.';

UPDATE notification_templates
SET subject_template = 'Your parcel has been picked up',
    body_template    = 'Parcel {{trackingNumber}} has been collected and is on its way.'
WHERE template_code = 'PARCEL_PICKED_UP'
  AND body_template = 'Courier {{courierName}} has picked up parcel {{trackingNumber}}. In-transit to distribution hub.';

UPDATE notification_templates
SET subject_template = 'Your parcel is out for delivery',
    body_template    = 'Parcel {{trackingNumber}} is out for delivery and should arrive today.'
WHERE template_code = 'PARCEL_OUT_FOR_DELIVERY'
  AND body_template = 'Parcel {{trackingNumber}} is out for delivery with {{courierName}}. Delivery expected shortly.';

UPDATE notification_templates
SET subject_template = 'Your parcel has been delivered',
    body_template    = 'Parcel {{trackingNumber}} was delivered on {{deliveredAt}}. Thank you for choosing SwiftDrop.'
WHERE template_code = 'PARCEL_DELIVERED'
  AND body_template = 'Your parcel {{trackingNumber}} was successfully delivered at {{deliveredAt}}. Thank you for choosing SwiftDrop.';

-- A failed delivery is final in dispatch (no automatic retry), so the old "We will retry shortly" was untrue.
UPDATE notification_templates
SET subject_template = 'We could not deliver your parcel',
    body_template    = 'We could not deliver parcel {{trackingNumber}}: {{failureReason}}. Our operations team has been notified and will arrange the next step.'
WHERE template_code = 'PARCEL_FAILED'
  AND body_template = 'Delivery attempt failed for parcel {{trackingNumber}}: {{failureReason}}. We will retry shortly.';

UPDATE notification_templates
SET subject_template = 'New delivery job assigned to you',
    body_template    = 'Parcel {{trackingNumber}} has been assigned to you for pickup in {{pickupDistrict}} and delivery to {{deliveryDistrict}}.'
WHERE template_code = 'COURIER_ASSIGNED'
  AND body_template = 'Parcel {{trackingNumber}} has been assigned to you. Please collect it from {{pickupDistrict}} for delivery to {{deliveryDistrict}}.';
