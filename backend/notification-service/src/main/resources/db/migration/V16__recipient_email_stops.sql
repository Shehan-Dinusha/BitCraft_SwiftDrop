-- A parcel's recipient has no account, so each recipient email carries a "Stop these emails" link for that
-- parcel. One row per parcel: the random token in the link, and when the recipient stopped the emails.
CREATE TABLE IF NOT EXISTS recipient_email_stops (
    order_id   UUID PRIMARY KEY,
    token      VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP WITH TIME ZONE
);
