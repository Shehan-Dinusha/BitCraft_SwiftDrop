-- V5: events already applied by this service, so a message delivered twice is only applied once.
-- The event id is claimed in the same transaction as the status update it guards.

CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
